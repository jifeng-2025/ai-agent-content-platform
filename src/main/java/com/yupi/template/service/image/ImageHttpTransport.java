package com.yupi.template.service.image;
import java.net.URI;import java.net.http.*;import java.nio.ByteBuffer;import java.time.Duration;import java.util.*;import java.util.concurrent.*;import org.springframework.stereotype.Component;
@Component public class ImageHttpTransport {
 public record Response(int status,String body,String requestId){}
 public Response post(String url,String header,String key,String json,int timeout){
  var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
  CompletableFuture<HttpResponse<byte[]>> future=null;
  try{
   var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout)).header("Content-Type","application/json").header(header,key).POST(HttpRequest.BodyPublishers.ofString(json)).build();
   future=client.sendAsync(request,info->new LimitedBody());
   var response=future.get(timeout,TimeUnit.SECONDS);
   String id=response.headers().firstValue("x-request-id").orElse(response.headers().firstValue("x-tt-logid").orElse(null));
   if(id!=null&&!id.matches("[A-Za-z0-9._:-]{1,200}"))id=null;
   return new Response(response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8),id);
  }catch(TimeoutException e){throw new ImageProviderException(ImageProviderException.Category.TIMEOUT,true);}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new ImageProviderException(ImageProviderException.Category.TRANSPORT,true);}
  catch(ExecutionException e){throw new ImageProviderException(e.getCause() instanceof java.net.http.HttpTimeoutException ? ImageProviderException.Category.TIMEOUT : ImageProviderException.Category.TRANSPORT,true);}
  catch(Exception e){throw new ImageProviderException(ImageProviderException.Category.TRANSPORT,true);}
  finally{if(future!=null&&!future.isDone())future.cancel(true);client.shutdownNow();}
 }
 private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();java.util.concurrent.Flow.Subscription subscription;long size;
  public CompletionStage<byte[]> getBody(){return delegate.getBody();}
  public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
  public void onNext(List<ByteBuffer> data){for(var b:data)size+=b.remaining();if(size>8*1024*1024){subscription.cancel();delegate.onError(new IllegalStateException("Response limit"));}else delegate.onNext(data);}
  public void onError(Throwable t){delegate.onError(t);}public void onComplete(){delegate.onComplete();}
 }
}
