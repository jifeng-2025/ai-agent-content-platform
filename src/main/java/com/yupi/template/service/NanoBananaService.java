package com.yupi.template.service;
import com.yupi.template.config.NanoBananaConfig;import com.yupi.template.model.dto.image.*;import com.yupi.template.model.enums.ImageMethodEnum;import com.yupi.template.service.image.*;import jakarta.annotation.Resource;import org.springframework.stereotype.Service;
/** Gemini native generateContent image adapter. Original platform: 编程导航学习圈. */
@Service public class NanoBananaService implements ImageSearchService {
 @Resource private NanoBananaConfig nanoBananaConfig;@Resource private ImageHttpTransport transport;
 @Resource private com.yupi.template.modelconfig.ModelSettings settings;@Resource private com.yupi.template.modelconfig.SafeModelHttp safeHttp;
 public String getFallbackImage(int position){return null;}
 public String searchImage(String keywords){return null;}
 public ImageData getImageData(ImageRequest request){

  var p=request.getProfile();if(p==null||!"gemini".equals(p.provider()))throw new IllegalArgumentException("缺少创建时固定的Gemini配置，请新建任务");
  if(p.configId()!=null){var spec=settings.get(p.configId(),p.configVersion());var secured=new ImageHttpTransport(){@Override public Response post(String u,String h,String k,String j,int t){return safeHttp.post(u,h,k,j,t);}};return ImageProviderProtocol.generate(secured,p,settings.key(spec),request.getEffectiveParam(true));}
  if(!isAvailable())throw new ImageProviderException(ImageProviderException.Category.NOT_CONFIGURED,false);
  return ImageProviderProtocol.generate(transport,p,nanoBananaConfig.getApiKey(),request.getEffectiveParam(true));
 }
 public boolean isAvailable(){if(settings!=null){var s=settings.selection("IMAGE","gemini");if(s!=null)return true;}return nanoBananaConfig!=null&&nanoBananaConfig.configured();}
 public ImageMethodEnum getMethod(){return ImageMethodEnum.NANO_BANANA;}
}
