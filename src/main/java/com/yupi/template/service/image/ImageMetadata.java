package com.yupi.template.service.image;
public record ImageMetadata(String provider,String model,String requestId,String jobId,java.util.Map<String,Long> usage,String errorCategory) implements java.io.Serializable {}
