package com.yupi.template.runtime;
import java.util.*;import com.yupi.template.model.dto.article.*;import com.yupi.template.service.*;import com.yupi.template.modelconfig.*;import com.yupi.template.agent.review.*;import org.springframework.stereotype.Service;import org.springframework.ai.chat.prompt.Prompt;
@Service @lombok.RequiredArgsConstructor public class QuickCreation {
 @jakarta.annotation.Resource private ArticleInterventionService interventions;
 private final com.yupi.template.agent.agents.TitleGeneratorAgent titles;
 private final com.yupi.template.agent.agents.OutlineGeneratorAgent outlines;
 private final QuickCreationStore quick;private final ArticleService articles;private final ArticleMediaProcessor media;private final ManagedChatModel model;private final ModelSettings settings;
 public void run(RuntimeStore.Work work)throws Exception{
  var t=work.ticket();var a=articles.getByTaskId(t.taskId());var s=new ArticleState();s.setTaskId(t.taskId());s.setTopic(a.getTopic());s.setStyle(a.getStyle());s.setUserDescription(a.getUserDescription());s.setContent(Objects.toString(a.getContent(),""));var title=new ArticleState.TitleResult();title.setMainTitle(a.getMainTitle()==null?a.getTopic():a.getMainTitle());title.setSubTitle(Objects.toString(a.getSubTitle(),""));s.setTitle(title);s.setEnabledImageMethods(Arrays.asList(com.yupi.template.utils.GsonUtils.fromJson(a.getEnabledImageMethods(),String[].class)));String phase=work.phase();
  if(a.getOutline()!=null){var outline=new ArticleState.OutlineResult();outline.setSections(Arrays.asList(com.yupi.template.utils.GsonUtils.fromJson(a.getOutline(),ArticleState.OutlineSection[].class)));s.setOutline(outline);}
  if("QUICK_ADVICE_RETRY".equals(work.action())){if("DONE".equals(phase))return;phase="Q_ADVICE";}
  if("QUICK_IMAGE_RETRY".equals(work.action())){if("DONE".equals(phase))return;var request=com.yupi.template.utils.GsonUtils.fromJson(work.requestJson(),QuickCreationController.RetryImage.class);media.retry(s,request.imageId(),request.prompt(),event->{});return;}
  if("Q_OUTLINE_WAIT".equals(phase)||"Q_TITLE_WAIT".equals(phase))return;
  if("Q_TITLE".equals(phase)){var input=Map.<String,Object>of("taskId",t.taskId(),"topic",s.getTopic(),"style",Objects.toString(s.getStyle(),""));String raw=RuntimeScope.call("QUICK_TITLE","dashscope",com.yupi.template.utils.GsonUtils.toJson(input),()->com.yupi.template.utils.GsonUtils.toJson(titles.apply(new com.alibaba.cloud.ai.graph.OverAllState(input)).get("titleOptions")));var choices=com.yupi.template.utils.GsonUtils.fromJson(raw,ArticleState.TitleOption[].class);if(choices==null||choices.length==0||Arrays.stream(choices).anyMatch(c->c==null||c.getMainTitle()==null||c.getMainTitle().isBlank()))throw new IllegalStateException("未收到有效标题");quick.titles(t,Arrays.asList(choices));return;}
  if("Q_OUTLINE".equals(phase)){var input=Map.<String,Object>of("taskId",t.taskId(),"mainTitle",s.getTitle().getMainTitle(),"subTitle",s.getTitle().getSubTitle(),"style",Objects.toString(s.getStyle(),""));String raw=RuntimeScope.call("QUICK_OUTLINE","dashscope",com.yupi.template.utils.GsonUtils.toJson(input),()->com.yupi.template.utils.GsonUtils.toJson(outlines.apply(new com.alibaba.cloud.ai.graph.OverAllState(input)).get("outline")));var outline=com.yupi.template.utils.GsonUtils.fromJson(raw,ArticleState.OutlineResult.class);if(outline==null||outline.getSections()==null||outline.getSections().isEmpty())throw new IllegalStateException("未收到有效大纲");quick.outline(t,outline);return;}
  if("DONE".equals(phase))return;
  if("Q_BODY".equals(phase)){
   if(s.getOutline()==null)throw new IllegalStateException("须先确认大纲");
   String prompt="文章标题："+s.getTitle().getMainTitle()+"\n严格遵循用户确认的大纲："+com.yupi.template.utils.GsonUtils.toJson(s.getOutline())+"\n根据主题直接写一篇约800字的完整中文图文文章正文。使用Markdown小标题，不输出规划或解释，不输出图片链接。不要编造精确数字或机构归因；不确定处用限定表达。主题："+s.getTopic()+"\n风格："+s.getStyle();
   String body=RuntimeScope.call("QUICK_BODY","dashscope",prompt,()->{StringBuilder out=new StringBuilder();long[] saved={0};model.stream(new Prompt(prompt)).doOnNext(chunk->{String value=chunk.getResult().getOutput().getText();if(value!=null)out.append(value);if(out.length()>64000)throw new IllegalStateException("正文超出长度限制");if(System.currentTimeMillis()-saved[0]>=200){quick.text(t,out.toString());saved[0]=System.currentTimeMillis();}}).blockLast();if(out.isEmpty())throw new IllegalStateException("文字服务未返回正文");return out.toString();});
   s.setContent(body);quick.stage(t,"Q_IMAGES",body);phase="Q_IMAGES";
  }
  if("Q_IMAGES".equals(phase)||"MEDIA".equals(phase)){
   try{media.generate(s,event->{});}catch(RuntimeStop stop){if(!"EXTERNAL_UNCERTAIN".equals(stop.status))throw stop;/* Keep the durable uncertain image call; do not submit it again. */}
   quick.adviceStage(t);phase="Q_ADVICE";
  }
  if("Q_ADVICE".equals(phase)){
   ReviewResult result=null;String reason=null;var draft=new ParagraphDraft(s.getContent());
   String prompt=AdvisoryReviewSkill.prompt(s,interventions.media(t.taskId()));
   try{String raw=RuntimeScope.call("QUICK_ADVICE","dashscope",prompt,()->{var spec=settings.taskText(t.taskId());return model.completeReview(spec,settings.key(spec),prompt,Math.min(spec.maxOutputTokens(),1800)).text();});result=ReviewJson.review(raw,draft.ids());}
   catch(RuntimeStop stop){if("FENCED".equals(stop.status)||"TIMED_OUT".equals(stop.status))throw stop;reason=quick.adviceFailure(t.taskId());}
   catch(RuntimeException failure){reason=failure instanceof ReviewJson.InvalidOutput?"评审返回格式不符合要求，未当作通过；可单独重新评审。":"评审未能完成，图文保留；可单独重新评审。";}
   quick.done(t,s,result,reason);
  }
 }
}
