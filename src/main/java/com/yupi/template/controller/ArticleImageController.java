package com.yupi.template.controller;
import com.yupi.template.storage.*;import com.yupi.template.service.*;import com.yupi.template.exception.*;import jakarta.servlet.http.HttpServletRequest;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.beans.factory.ObjectProvider;import java.io.*;import java.util.*;import java.util.zip.*;import java.nio.charset.StandardCharsets;
@RestController @RequiredArgsConstructor public class ArticleImageController {
 private final ObjectProvider<LocalImageStorage> storage;private final UserService users;private final ArticleService articles;private final JdbcTemplate jdbc;
 private LocalImageStorage local(){var value=storage.getIfAvailable();if(value==null)throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);return value;}
 @GetMapping("/images/{id}") public ResponseEntity<byte[]> image(@PathVariable String id,HttpServletRequest request){
  var user=users.getLoginUser(request);if(!id.matches("[a-f0-9]{32}"))throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
  var tasks=jdbc.queryForList("SELECT taskId FROM article WHERE userId=? AND isDelete=0 AND images LIKE ?",String.class,user.getId(),"%/api/images/"+id+"%");
  if(tasks.isEmpty())throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
  try{return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").contentType(MediaType.IMAGE_PNG).body(local().read(id));}catch(ImageStorageException e){throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);}
 }
 @GetMapping("/article/{taskId}/export.zip") public ResponseEntity<byte[]> export(@PathVariable String taskId,HttpServletRequest request)throws IOException{
  var article=articles.getArticleDetail(taskId,users.getLoginUser(request));
  String md=markdown(article);
  var files=new LinkedHashMap<String,byte[]>();long total=0;
  if(article.getImages()!=null)for(var image:article.getImages())if(ImageReferences.local(image.getUrl())){
   String id=image.getUrl().substring("/api/images/".length()),path="images/"+id+".png";if(files.containsKey(path))continue;
   if(files.size()>=16)throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片过多");byte[] bytes=local().read(id);total+=bytes.length;if(total>41943040)throw new BusinessException(ErrorCode.PARAMS_ERROR,"导出过大");files.put(path,bytes);md=md.replace(image.getUrl(),path);
  }
  // No arbitrary path or remote URL is fetched. Old COS URLs remain links in Markdown.
  files.put("article.md",md.getBytes(StandardCharsets.UTF_8));
  String html=OfflineArticleHtml.render(md);
  files.put("index.html",html.getBytes(StandardCharsets.UTF_8));files.put("README.txt","解压整个压缩包后打开 index.html，或用 Markdown 编辑器预览 article.md。请保留 images 文件夹和正文的相对位置。本地图片已包含，无需登录或联网；旧外部图片仍是网络链接。".getBytes(StandardCharsets.UTF_8));
  var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){for(var file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(file.getValue());zip.closeEntry();}}
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition","attachment; filename=article.zip").header("Cache-Control","private, no-store").body(bytes.toByteArray());
 }

 @GetMapping("/article/{taskId}/export.md") public ResponseEntity<byte[]> markdownExport(@PathVariable String taskId,HttpServletRequest request){
  var article=articles.getArticleDetail(taskId,users.getLoginUser(request));String md=markdown(article);var seen=new HashSet<String>();long total=0;
  if(article.getImages()!=null)for(var image:article.getImages())if(ImageReferences.local(image.getUrl())&&seen.add(image.getUrl())){if(seen.size()>16)throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片过多");byte[] imageBytes=local().read(image.getUrl().substring(12));total+=imageBytes.length;if(total>41943040)throw new BusinessException(ErrorCode.PARAMS_ERROR,"导出过大，请使用图文ZIP");md=md.replace(image.getUrl(),"data:image/png;base64,"+Base64.getEncoder().encodeToString(imageBytes));}
  if(java.util.regex.Pattern.compile("/api/images/[a-f0-9]{32}").matcher(md).find())throw new BusinessException(ErrorCode.PARAMS_ERROR,"正文图片与文章归属记录不一致，无法完整导出");
  md+="\n\n<!-- 本文件内嵌本地图片。请使用支持data图片的Markdown预览器；若编辑器不显示，请下载图文ZIP并完整解压。旧外部图片仍需要联网。 -->\n";
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8")).header("Content-Disposition","attachment; filename=article.md").header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(md.getBytes(StandardCharsets.UTF_8));
 }
 @GetMapping("/article/{taskId}/export.html") public ResponseEntity<byte[]> htmlExport(@PathVariable String taskId,HttpServletRequest request){
  String md=new String(markdownExport(taskId,request).getBody(),StandardCharsets.UTF_8);md=md.substring(0,md.lastIndexOf("<!--"));
  return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).header("Content-Disposition","attachment; filename=article.html").header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(OfflineArticleHtml.render(md).getBytes(StandardCharsets.UTF_8));
 }
 private static String markdown(com.yupi.template.model.vo.ArticleVO a){String body=a.getFullContent();if(body==null||body.isBlank())body=Objects.toString(a.getContent(),"");String md="# "+Objects.toString(a.getMainTitle(),"")+"\n\n";if(a.getSubTitle()!=null&&!a.getSubTitle().isBlank())md+="> "+a.getSubTitle()+"\n\n";md+=body;if(a.getImages()!=null)for(var image:a.getImages())if(image.getUrl()!=null&&ImageReferences.allowed(image.getUrl())&&!md.contains(image.getUrl()))md+="\n\n![配图]("+image.getUrl()+")";return md;}
}
