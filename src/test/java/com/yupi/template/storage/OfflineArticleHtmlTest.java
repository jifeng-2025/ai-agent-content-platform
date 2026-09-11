package com.yupi.template.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OfflineArticleHtmlTest {
 @Test void rendersTypographyAndInlinePictures(){String h=OfflineArticleHtml.render("# 标题\n\n> 副标题\n\n## 第一节\n\n前文 **重点**\n\n![图](images/"+"a".repeat(32)+".png)\n\n后文\n\n- 项目一\n- 项目二\n\n1. 步骤\n\n```java\n<script>\n```");assertTrue(h.contains("<h1>标题</h1>"));assertTrue(h.contains("<blockquote>副标题</blockquote>"));assertTrue(h.contains("<strong>重点</strong>"));assertTrue(h.contains("<ul><li>项目一</li><li>项目二</li></ul>"));assertTrue(h.contains("<ol><li>步骤</li></ol>"));assertTrue(h.indexOf("前文")<h.indexOf("<img"));assertTrue(h.indexOf("<img")<h.indexOf("后文"));assertTrue(h.contains("max-width:860px"));assertFalse(h.contains("<script>"));}
 @Test void escapesActiveContentAndRejectsUnsafeImages(){String h=OfflineArticleHtml.render("<script>alert(1)</script>\n\n![x](javascript:alert)\n\n![x](file:///etc/passwd)\n\n![x](data:text/html;base64,abc)");assertFalse(h.contains("<script>"));assertFalse(h.contains("<img"));assertTrue(h.contains("&lt;script&gt;"));}
 @Test void offlinePreviewFixture()throws Exception {
  String path=System.getProperty("offline.preview");if(path==null)return;
  var root=java.nio.file.Path.of(path);java.nio.file.Files.createDirectories(root.resolve("images"));
  var img=new java.awt.image.BufferedImage(960,480,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(new java.awt.Color(215,239,225));g.fillRect(0,0,960,480);g.setColor(new java.awt.Color(52,138,97));g.fillRoundRect(260,100,440,280,40,40);g.setColor(java.awt.Color.WHITE);g.setFont(new java.awt.Font("SansSerif",1,40));g.drawString("OFFLINE IMAGE",310,250);g.dispose();javax.imageio.ImageIO.write(img,"png",root.resolve("images/"+"a".repeat(32)+".png").toFile());
  String md="# 把灵感变成作品：我的创作实践\n\n> 从想法到图文，让每一个小步骤都看得见\n\n## 第一步：确定清晰的创作目标\n\n好的作品始于一个具体的问题。先写下你想帮助的读者，再用自己的经验说明：**为什么这件事值得尝试**。\n\n- 确定主题和目标读者\n- 选择标题，确认文章大纲\n\n## 第二步：用图文讲清楚过程\n\n图片放在相关段落之间，帮助读者理解，而不是打断阅读。下面是用于检查离线排版的测试图片。\n\n![离线测试配图](images/"+"a".repeat(32)+".png)\n\n保存正文和图片后，即使断开网络，也能继续阅读和分享。\n\n### 最后：回顾与改进\n\n1. 检查内容是否完整\n2. 保留重要细节，优化表达\n";
  java.nio.file.Files.writeString(root.resolve("index.html"),OfflineArticleHtml.render(md));java.nio.file.Files.writeString(root.resolve("article.md"),md);assertTrue(java.nio.file.Files.size(root.resolve("index.html"))>1000);
 }
 @Test void permitsEmbeddedPng(){assertTrue(OfflineArticleHtml.render("![配图](data:image/png;base64,YWJj)").contains("<img alt=\"配图\" src=\"data:image/png;base64,YWJj\">"));}
}
