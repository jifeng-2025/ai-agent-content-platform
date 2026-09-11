package com.yupi.template.service;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.yupi.template.agent.agents.ImageAnalyzerAgent;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.model.enums.ImageMethodEnum;
import com.yupi.template.repository.ArticleMediaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ArticleMediaProcessor {
    private final ImageAnalyzerAgent analyzer;
    private final ImageGenerationTool images;
    private final ArticleMediaStore store;
    private final ArticleInterventionService interventions;
    @jakarta.annotation.Resource private com.yupi.template.runtime.QuickCreationStore quick;
    @jakarta.annotation.Resource private com.yupi.template.service.image.ImageProfileStore profiles;

    public void generate(ArticleState state, Consumer<String> events) throws Exception {
        var media=interventions.media(state.getTaskId());
        if(media==null) media=plan(state);
        persist(state,media,"PROCESSING",events);
        for(int i=0;i<media.slots().size();i++) {
            var slot=media.slots().get(i);
            if("SUCCESS".equals(slot.status()) || "DEGRADED".equals(slot.status()) || (com.yupi.template.runtime.RuntimeScope.active() && "FAILED".equals(slot.status())))continue;
            media=replace(media,i,generate(slot));persist(state,media,"PROCESSING",events);
        }
        finish(state,media,events);
    }
    public void retry(ArticleState state, String imageId, Consumer<String> events) {
        retry(state,imageId,null,events);
    }
    public void retry(ArticleState state,String imageId,String prompt,Consumer<String> events){
        var media=Objects.requireNonNull(interventions.media(state.getTaskId()));
        int index=-1;for(int i=0;i<media.slots().size();i++)if(media.slots().get(i).id().equals(imageId))index=i;
        if(index<0)throw new IllegalArgumentException("Unknown image");
        // Exactly one tool call. All other slot objects and the immutable text template are reused.
        var slot=media.slots().get(index);
        if(prompt!=null&&!prompt.isBlank()){var req=com.yupi.template.utils.GsonUtils.fromJson(com.yupi.template.utils.GsonUtils.toJson(slot.requirement()),ArticleState.ImageRequirement.class);req.setPrompt(prompt);slot=new MediaState.Slot(slot.id(),req,slot.result(),slot.status(),slot.attempts(),slot.error());}
        media=replace(media,index,generate(slot));
        finish(state,media,events);
    }
    @SuppressWarnings("unchecked")
    private MediaState plan(ArticleState state) throws Exception {
        var input=new HashMap<String,Object>();input.put("mainTitle",state.getTitle().getMainTitle());input.put("content",state.getContent());
        input.put("enabledImageMethods",state.getEnabledImageMethods());input.put("taskId",state.getTaskId());
        List<ArticleState.ImageRequirement> requests;
        if(quick!=null&&quick.isQuick(state.getTaskId())){requests=ArticleImagePlacement.plan(state,quick.imageCount(state.getTaskId()));
        } else if (com.yupi.template.service.DemoImageService.selected(state.getEnabledImageMethods())) {
            requests=com.yupi.template.service.DemoImageService.plan();
        } else if (com.yupi.template.runtime.RuntimeScope.active()) {
            String json=com.yupi.template.runtime.RuntimeScope.call("IMAGE_PLAN","dashscope",com.yupi.template.utils.GsonUtils.toJson(input),()->com.yupi.template.utils.GsonUtils.toJson(analyzer.apply(new OverAllState(input)).get("imageRequirements")));
            requests=Arrays.asList(com.yupi.template.utils.GsonUtils.fromJson(json,ArticleState.ImageRequirement[].class));
        } else {
            var result=analyzer.apply(new OverAllState(input));requests=(List<ArticleState.ImageRequirement>)result.get("imageRequirements");
        }
        if(requests==null || requests.size()>8)throw new IllegalStateException("Invalid image plan");
        var slots=new ArrayList<MediaState.Slot>();StringBuilder template=new StringBuilder(state.getContent());
        for(int i=0;i<requests.size();i++) {
            var req=requests.get(i);var method=ImageMethodEnum.getByValue(req.getImageSource());
            if(method==null || (method.isFallback() && !(method==ImageMethodEnum.DEMO && com.yupi.template.service.DemoImageService.selected(state.getEnabledImageMethods()))) || (state.getEnabledImageMethods()!=null && !state.getEnabledImageMethods().isEmpty() && !state.getEnabledImageMethods().contains(req.getImageSource())))throw new IllegalStateException("Image method not allowed");
            if(com.yupi.template.service.image.ImageProfile.paid(req.getImageSource()))req.setProfile(profiles.find(state.getTaskId(),req.getImageSource()));
            req.setPosition(i+1);req.setPlaceholderId("{{A2_IMAGE_"+(i+1)+"}}");
            slots.add(new MediaState.Slot("image-"+(i+1),req,null,"PENDING",0,null));

        }
        // The analyzer's rewritten body is deliberately not used. Preserve the reviewed text byte-for-byte.
        return new MediaState(template.toString(),slots);
    }
    private ImageGenerationTool.ImageGenerationResult generateTool(ArticleState.ImageRequirement req) {
        if(com.yupi.template.service.image.ImageProfile.paid(req.getImageSource()))return images.generateImageDirect(req.getImageSource(),req.getKeywords(),req.getPrompt(),req.getPosition(),req.getType(),req.getSectionTitle(),req.getPlaceholderId(),req.getProfile());
        return images.generateImageDirect(req.getImageSource(),req.getKeywords(),req.getPrompt(),req.getPosition(),req.getType(),req.getSectionTitle(),req.getPlaceholderId());
    }
    private MediaState.Slot generate(MediaState.Slot slot) {
        var req=slot.requirement();
        try {
            String raw=com.yupi.template.runtime.RuntimeScope.call("IMAGE:"+slot.id(),"image:"+req.getImageSource(),com.yupi.template.utils.GsonUtils.toJson(req),()->com.yupi.template.utils.GsonUtils.toJson(generateTool(req)));
            var result=com.yupi.template.utils.GsonUtils.fromJson(raw,ImageGenerationTool.ImageGenerationResult.class);
            if(!result.isSuccess() || result.getUrl()==null || !com.yupi.template.storage.ImageReferences.allowed(result.getUrl()))throw new IllegalStateException("Image unavailable");
            var image=new ArticleState.ImageResult();image.setPosition(req.getPosition());image.setPlaceholderId(req.getPlaceholderId());image.setUrl(result.getUrl());
            image.setMetadata(result.getMetadata());image.setMethod(result.getMethod());image.setKeywords(req.getKeywords());image.setSectionTitle(req.getSectionTitle());image.setDescription(Objects.toString(req.getSectionTitle(),"配图"));
            var method=ImageMethodEnum.getByValue(result.getMethod());
            boolean degraded=method==null || method.isFallback() || !Objects.equals(req.getImageSource(),result.getMethod());
            return new MediaState.Slot(slot.id(),req,image,degraded?"DEGRADED":"SUCCESS",slot.attempts()+1,degraded?"演示/占位或降级来源（非AI生图），未保证图文语义匹配":null);
        } catch(Exception e) {
            if(e instanceof com.yupi.template.runtime.RuntimeStop stop)throw stop;
            // A failed retry retains an existing usable image but exposes the failed attempt.
            return new MediaState.Slot(slot.id(),req,slot.result(),"FAILED",slot.attempts()+1,e instanceof com.yupi.template.storage.ImageStorageException ? "STORAGE_FAILED：图片保存失败，已生成结果保留；修复存储后重试" : e instanceof com.yupi.template.service.image.ImageProviderException ? e.getMessage() : "本次配图失败，可再次单张重试");
        }
    }
    private MediaState replace(MediaState m,int i,MediaState.Slot slot) {
        var slots=new ArrayList<>(m.slots());slots.set(i,slot);return new MediaState(m.template(),slots);
    }
    private void finish(ArticleState s,MediaState m,Consumer<String> events) {
        String status=m.slots().stream().anyMatch(slot->List.of("FAILED","PENDING").contains(slot.status()))?"IMAGES_FAILED":"COMPLETED";
        persist(s,m,status,events);s.setPhase(status);s.setMediaHandled(true);
    }
    private void persist(ArticleState state,MediaState media,String status,Consumer<String> events) {
        String full=media.template();var placements=new LinkedHashMap<String,String>();var result=new ArrayList<ArticleState.ImageResult>();
        for(var slot:media.slots()) {
            String text="[配图暂缺："+slot.id()+"，可单张重试]";
            if(slot.result()!=null) {
                result.add(slot.result());
                text="![配图]("+slot.result().getUrl()+")";
                if("DEGRADED".equals(slot.status()) || !Objects.equals(slot.requirement().getImageSource(),slot.result().getMethod()))text+="\n\n> 演示/占位或降级图片，非AI生图，未保证图文语义匹配。";
                if("FAILED".equals(slot.status()))text+="\n\n> 本次重试失败，保留上次图片。";
            }
            placements.put(slot.id(),text);
        }
        full=ArticleImagePlacement.render(media,placements);
        state.setImages(result);state.setFullContent(full);state.setCoverImage(result.isEmpty()?null:result.getFirst().getUrl());
        store.save(state,media,status);
        try { events.accept("MEDIA_UPDATED"); } catch(RuntimeException ignored) { /* durable GET is authoritative */ }
    }
}
