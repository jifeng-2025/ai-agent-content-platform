package com.yupi.template.modelconfig;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
@org.springframework.stereotype.Component @org.springframework.core.annotation.Order(-100)
public class ModelAdminGuard extends org.springframework.web.filter.OncePerRequestFilter {
 @org.springframework.beans.factory.annotation.Value("${models.admin.origin:http://localhost}") private String origin;
 @org.springframework.beans.factory.annotation.Value("${models.admin.trust-private-proxy:false}") private boolean proxy;
 protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith(r.getContextPath()+"/admin/models");}
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
  try{
   URI expected=URI.create(origin);var remote=InetAddress.getByName(r.getRemoteAddr());boolean trusted=proxy&&remote.isSiteLocalAddress();boolean localHost=java.util.Set.of("localhost","127.0.0.1","[::1]").contains(expected.getHost());
   boolean transport="https".equals(expected.getScheme())&&(r.isSecure()||(trusted&&"https".equals(r.getHeader("X-Forwarded-Proto"))));
   if(!transport&&!("http".equals(expected.getScheme())&&localHost&&(remote.isLoopbackAddress()||trusted)))throw new Exception();
   String actual=r.getHeader("Origin"),referer=r.getHeader("Referer");if(actual==null){if(referer==null||!referer.startsWith(origin+"/"))throw new Exception();}else if(!origin.equals(actual))throw new Exception();
   if("cross-site".equals(r.getHeader("Sec-Fetch-Site")))throw new Exception();
   if(!"GET".equals(r.getMethod())){if(!"POST".equals(r.getMethod())||r.getContentType()==null||!r.getContentType().startsWith("application/json"))throw new Exception();var session=r.getSession(false);String token=session==null?null:(String)session.getAttribute("model-csrf");String supplied=r.getHeader("X-Model-CSRF");if(token==null||supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new Exception();}
  }catch(Exception e){response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":40300,\"message\":\"模型设置仅允许配置的同源HTTPS或回环开发访问，请刷新登录并检查 MODEL_ADMIN_ORIGIN\"}");return;}
  chain.doFilter(r,response);
 }
}
