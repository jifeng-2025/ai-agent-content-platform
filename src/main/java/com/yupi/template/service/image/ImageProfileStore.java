package com.yupi.template.service.image;
import com.yupi.template.config.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import lombok.RequiredArgsConstructor;import com.yupi.template.utils.GsonUtils;
@Service @RequiredArgsConstructor public class ImageProfileStore {
 private final JdbcTemplate jdbc;private final NanoBananaConfig gemini;private final DoubaoConfig doubao;
 @org.springframework.beans.factory.annotation.Autowired private com.yupi.template.modelconfig.ModelSettings settings;
 public ImageProfile snapshot(String method){
  if(settings!=null&&ImageProfile.paid(method)){var selected=settings.selection("IMAGE","NANO_BANANA".equals(method)?"gemini":"doubao");if(selected!=null)return new ImageProfile(selected.protocol(),selected.model(),selected.endpoint(),selected.imageSize(),selected.aspectRatio(),selected.maxOutputTokens(),selected.timeoutSeconds(),null,selected.id(),selected.version());}
  ImageProfile p=switch(method){case "NANO_BANANA"->new ImageProfile("gemini",gemini.getModel(),gemini.getBaseUrl(),gemini.getImageSize(),gemini.getAspectRatio(),gemini.getMaxOutputTokens(),gemini.getTimeoutSeconds());case "DOUBAO"->new ImageProfile("doubao",doubao.getModel(),doubao.getBaseUrl(),doubao.getImageSize(),"1:1",2048,doubao.getTimeoutSeconds());default->null;};if(p!=null)p.validate();return p;
 }
 public void save(String taskId,java.util.List<String> methods){for(String method:methods){var p=snapshot(method);if(p!=null)jdbc.update("INSERT INTO article_image_profile(taskId,method,profileJson) VALUES (?,?,?)",taskId,method,GsonUtils.toJson(p.forTask(taskId)));}}
 public ImageProfile find(String taskId,String method){
  if(!ImageProfile.paid(method))return null;
  if(taskId==null)throw new IllegalArgumentException("图片任务身份缺失");
  var rows=jdbc.queryForList("SELECT profileJson FROM article_image_profile WHERE taskId=? AND method=?",taskId,method);
  if(rows.isEmpty())throw new IllegalArgumentException("旧任务未固定图片模型，请新建任务；原图片仍可查看");
  return GsonUtils.fromJson((String)rows.getFirst().get("profileJson"),ImageProfile.class);
 }
}
