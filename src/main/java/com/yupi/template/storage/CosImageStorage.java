package com.yupi.template.storage;
import org.springframework.stereotype.Service;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import lombok.RequiredArgsConstructor;import com.yupi.template.model.dto.image.ImageData;import com.yupi.template.service.CosService;
@Service @org.springframework.context.annotation.Primary @RequiredArgsConstructor @ConditionalOnProperty(name="article.storage.type",havingValue="cos")
public class CosImageStorage implements ImageStorage {private final CosService cos;private final LocalImageStorage raster;public String save(ImageData data,String folder){String url=cos.uploadImageData(ImageData.fromBytes(raster.normalize(data),"image/png"),folder);if(url==null)throw new ImageStorageException();return url;}}
