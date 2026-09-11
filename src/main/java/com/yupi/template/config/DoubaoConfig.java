package com.yupi.template.config;
import lombok.Data;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.context.annotation.Configuration;
@Configuration @ConfigurationProperties(prefix="doubao") @Data
public class DoubaoConfig {
 @lombok.ToString.Exclude private String apiKey; private String model=""; // Account model/endpoint ID must be explicitly configured.
 private String baseUrl="https://ark.cn-beijing.volces.com/api/v3";
 private String imageSize="2K"; private int timeoutSeconds=120;
 public boolean configured(){return apiKey!=null&&apiKey.trim().length()>=16&&!apiKey.contains("your-")&&!apiKey.contains("placeholder")&&model!=null&&!model.isBlank();}
}
