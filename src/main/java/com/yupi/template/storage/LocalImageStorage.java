package com.yupi.template.storage;
import com.yupi.template.model.dto.image.ImageData;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;import java.io.*;import java.net.*;import java.util.*;import javax.imageio.ImageIO;
@Service
public class LocalImageStorage implements ImageStorage {
 private final Path root; private final int maxBytes;
 public LocalImageStorage(@Value("${article.storage.local.root:./data/images}") String root,@Value("${article.storage.max-bytes:5242880}") int maxBytes){this.root=Path.of(root).toAbsolutePath().normalize();this.maxBytes=maxBytes;if(this.root.equals(this.root.getRoot())||maxBytes<1||maxBytes>16777216)throw new IllegalArgumentException("Invalid storage root or size");}
 public byte[] normalize(ImageData data){
  try{
   byte[] bytes;
   if(data.getDataType()==ImageData.DataType.URL){
    URI uri=URI.create(data.getUrl());if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IOException();
    for(var address:InetAddress.getAllByName(uri.getHost()))if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())throw new IOException();
    var connection=(HttpURLConnection)uri.toURL().openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
    try{if(connection.getResponseCode()!=200||connection.getContentLengthLong()>maxBytes)throw new IOException();try(var in=connection.getInputStream()){bytes=in.readNBytes(maxBytes+1);}}finally{connection.disconnect();}
   }else {if(data.getDataType()==ImageData.DataType.DATA_URL&&data.getUrl().length()>maxBytes*2L)throw new IOException();bytes=data.getImageBytes();}
   if(bytes==null||bytes.length==0||bytes.length>maxBytes)throw new IOException();
   // Decode only raster formats and re-encode PNG: active SVG/HTML and embedded trailing data are never served.
   try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();try{reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(!Set.of("png","jpeg","jpg","gif").contains(format))throw new IOException();int w=reader.getWidth(0),h=reader.getHeight(0);if(w<=0||h<=0||(long)w*h>16000000)throw new IOException();var image=reader.read(0);for(int attempt=0;attempt<6;attempt++){var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);if(out.size()<=maxBytes)return out.toByteArray();double scale=Math.min(0.85,Math.sqrt((double)maxBytes/out.size())*0.9);int nw=Math.max(1,(int)(image.getWidth()*scale)),nh=Math.max(1,(int)(image.getHeight()*scale));var resized=new java.awt.image.BufferedImage(nw,nh,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=resized.createGraphics();try{g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,0,0,nw,nh,null);}finally{g.dispose();}image=resized;}throw new IOException();}finally{reader.dispose();}}
  }catch(Exception e){throw new ImageStorageException();}
 }
 public String save(ImageData data,String ignored){return savePng(normalize(data));}
 public String savePng(byte[] bytes){
  try{Files.createDirectories(root);for(Path part=root;part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException();if(Files.isSymbolicLink(root))throw new IOException();String id=UUID.randomUUID().toString().replace("-","");Path temp=Files.createTempFile(root,"pending-",".tmp"),target=root.resolve(id+".png");try{Files.write(temp,bytes,StandardOpenOption.TRUNCATE_EXISTING);try(var ch=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){ch.force(true);}Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);return "/api/images/"+id;}finally{Files.deleteIfExists(temp);}}
  catch(Exception e){throw new ImageStorageException();}
 }
 public byte[] read(String id){try{if(id==null||!id.matches("[a-f0-9]{32}"))throw new IOException();Path f=root.resolve(id+".png");for(Path part=root;part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException();if(Files.isSymbolicLink(root)||Files.isSymbolicLink(f)||!Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)||Files.size(f)>maxBytes)throw new IOException();return Files.readAllBytes(f);}catch(Exception e){throw new ImageStorageException();}}
}
