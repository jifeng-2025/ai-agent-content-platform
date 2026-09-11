package com.yupi.template.service;
import com.yupi.template.model.dto.image.*;
import com.yupi.template.storage.*;
import com.yupi.template.config.NanoBananaConfig;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.impl.ArticleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.Path;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class A4DemoTest {
 @TempDir Path root;
 LocalImageStorage local(){return new LocalImageStorage(root.toString(),5242880);}
 @Test void offlinePngSurvivesNewStorageInstance(){var local=local();var service=new DemoImageService(local,local,false);var result=service.create(ImageRequest.builder().build());assertEquals("DEMO_PNG",result.getMethod().getValue());assertTrue(result.getMethod().isFallback());assertArrayEquals(DemoImageService.placeholder(),local().read(result.getUrl().substring(12)));}
 @Test void networkFailureUsesPng(){var local=local();var service=new DemoImageService(local,local,true){protected byte[] download()throws IOException{throw new IOException("offline");}};assertEquals("DEMO_PNG",service.create(ImageRequest.builder().build()).getMethod().getValue());}
 @Test void reachablePhotoRetainsSource(){var local=local();var service=new DemoImageService(local,local,true){protected byte[] download(){return DemoImageService.placeholder();}};assertEquals("PICSUM",service.create(ImageRequest.builder().build()).getMethod().getValue());}
 @Test void hostileRedirectsRejected(){for(String url:List.of("http://fastly.picsum.photos/id/1/800/600.jpg","https://localhost/id/1/800/600.jpg","https://fastly.picsum.photos.evil.test/id/1/800/600.jpg","https://user@fastly.picsum.photos/id/1/800/600.jpg","https://fastly.picsum.photos:8080/id/1/800/600.jpg","https://fastly.picsum.photos/../secret"))assertThrows(IOException.class,()->DemoImageService.validateUri(URI.create(url),true));assertDoesNotThrow(()->DemoImageService.validateUri(URI.create("https://fastly.picsum.photos/id/1/800/600.jpg?hmac=x"),true));}
 @Test void storageFailureDoesNotFallbackOrRegenerate(){var sink=mock(ImageStorage.class);when(sink.save(any(),any())).thenThrow(new ImageStorageException());var service=new DemoImageService(sink,local(),false);assertThrows(ImageStorageException.class,()->service.create(ImageRequest.builder().build()));verify(sink,times(1)).save(any(),any());}
 @Test void explicitDemoNeverTouchesModel(){var strategy=new ImageServiceStrategy();var model=mock(ImageSearchService.class);var local=local();ReflectionTestUtils.setField(strategy,"demo",new DemoImageService(local,local,false));ReflectionTestUtils.setField(strategy,"imageSearchServices",List.of(model));assertEquals("DEMO_PNG",strategy.getImageAndUpload("DEMO",ImageRequest.builder().build()).getMethod().getValue());verifyNoInteractions(model);}
 @Test void backendRejectsMissingGeminiForAdminAndRejectsMixedDemo(){var service=new ArticleServiceImpl();var config=new NanoBananaConfig();config.setApiKey("xxx");assertFalse(config.configured());ReflectionTestUtils.setField(service,"imageConfig",config);var user=new User();user.setUserRole("admin");assertThrows(com.yupi.template.exception.BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"validateImageMethods",List.of("NANO_BANANA"),user));assertThrows(com.yupi.template.exception.BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"validateImageMethods",List.of("DEMO","PEXELS"),user));assertEquals(List.of("DEMO"),ReflectionTestUtils.invokeMethod(service,"processImageMethods",null,user));}
 @Test void realProductionContextWithoutImageOrCosKey(){new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class)).withUserConfiguration(NanoBananaConfig.class,com.yupi.template.config.CosConfig.class,CosService.class,LocalImageStorage.class,CosImageStorage.class,DemoImageService.class).withInitializer(c->{try{for(var source:new YamlPropertySourceLoader().load("prod",new ClassPathResource("application-prod.yml")))c.getEnvironment().getPropertySources().addLast(source);}catch(Exception e){throw new RuntimeException(e);}}).withPropertyValues("article.storage.local.root="+root,"article.images.demo.remote-enabled=false").run(c->{assertNull(c.getStartupFailure());assertFalse(c.getBean(NanoBananaConfig.class).configured());assertTrue(c.getBeansOfType(CosService.class).isEmpty());assertEquals("DEMO_PNG",c.getBean(DemoImageService.class).create(ImageRequest.builder().build()).getMethod().getValue());});}
}
