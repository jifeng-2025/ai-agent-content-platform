package com.yupi.template.storage;

import java.util.regex.Pattern;

/** Offline renderer: raw HTML is escaped; no scripts or external styles are loaded. */
public final class OfflineArticleHtml {
 private OfflineArticleHtml() {}
 public static String render(String markdown) {
  var body = new StringBuilder(); var paragraph = new StringBuilder();
  boolean code = false; String list = "";
  for (String line : markdown.replace("\r", "").split("\n", -1)) {
   if (line.startsWith("```")) { flush(body, paragraph); if (!list.isEmpty()) { body.append("</").append(list).append('>'); list=""; } body.append(code?"</code></pre>":"<pre><code>"); code=!code; continue; }
   if (code) { body.append(escape(line)).append('\n'); continue; }
   var heading=Pattern.compile("^(#{1,6})\\s+(.+)$").matcher(line);
   var item=Pattern.compile("^\\s*(?:([-*+])|\\d+[.)])\\s+(.+)$").matcher(line);
   if (!item.matches() && !list.isEmpty()) {body.append("</").append(list).append('>');list="";}
   if (line.isBlank()) {flush(body,paragraph);continue;}
   if (heading.matches()) {flush(body,paragraph);int n=heading.group(1).length();body.append("<h").append(n).append('>').append(inline(heading.group(2))).append("</h").append(n).append('>');}
   else if (item.matches()) {flush(body,paragraph);String kind=item.group(1)==null?"ol":"ul";if(!kind.equals(list)){if(!list.isEmpty())body.append("</").append(list).append('>');body.append('<').append(kind).append('>');list=kind;}body.append("<li>").append(inline(item.group(2))).append("</li>");}
   else if (line.startsWith("> ")) {flush(body,paragraph);body.append("<blockquote>").append(inline(line.substring(2))).append("</blockquote>");}
   else if (line.matches("\\s*(---+|\\*\\*\\*+)\\s*")) {flush(body,paragraph);body.append("<hr>");}
   else {if(!paragraph.isEmpty())paragraph.append('\n');paragraph.append(line);}
  }
  flush(body,paragraph);if(code)body.append("</code></pre>");if(!list.isEmpty())body.append("</").append(list).append('>');
  return """
   <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
   <meta name="viewport" content="width=device-width, initial-scale=1">
   <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self' data: https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
   <title>图文阅读</title><style>
   *{box-sizing:border-box}body{margin:0;background:#f4f8f6;color:#24342e;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;line-height:1.95}
   article{max-width:860px;margin:48px auto;padding:48px 56px;background:#fff;border:1px solid #e1ebe5;border-radius:20px;box-shadow:0 12px 40px #203b2910;overflow-wrap:anywhere}
   h1{font-size:2rem;line-height:1.4;margin:0 0 28px;color:#173e2c}h2{font-size:1.5rem;margin-top:2em}h3,h4,h5,h6{font-size:1.2rem;margin-top:1.8em}p{margin:1.1em 0;font-size:17px}img{display:block;max-width:100%;max-height:680px;width:auto;height:auto;margin:28px auto;border-radius:12px;object-fit:contain}blockquote{margin:20px 0;padding:12px 20px;border-left:4px solid #32ac73;background:#f0f8f3;color:#567062}strong{color:#173e2c}li{margin:.45em 0}pre{overflow:auto;background:#f3f5f4;padding:20px;border-radius:8px}code{font-family:monospace;background:#f3f5f4}hr{border:0;border-top:1px solid #e1ebe5;margin:32px 0}a{color:#138654}
   @media(max-width:600px){article{margin:12px;padding:24px 20px;border-radius:14px}h1{font-size:1.6rem}p{font-size:16px}img{max-height:480px}}@media print{body{background:white}article{margin:0;border:0;box-shadow:none;padding:0}img{break-inside:avoid}}
   </style></head><body><article>
   """ + body + "</article></body></html>";
 }
 private static void flush(StringBuilder body,StringBuilder p){if(!p.isEmpty()){body.append("<p>").append(inline(p.toString())).append("</p>");p.setLength(0);}}
 private static String inline(String text){
  String s=escape(text);
  // Only the generated image folder, inline PNG, and legacy HTTPS images are permitted.
  s=s.replaceAll("!\\[([^\\]\\r\\n]*)\\]\\(((?:images/[a-f0-9]{32}\\.png|data:image/png;base64,[A-Za-z0-9+/=]+|https://[^\\s<>]+?))\\)","<img alt=\"$1\" src=\"$2\">");
  s=s.replaceAll("\\*\\*([^*\\n]+)\\*\\*","<strong>$1</strong>");
  s=s.replaceAll("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)","<em>$1</em>");
  return s;
 }
 private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
