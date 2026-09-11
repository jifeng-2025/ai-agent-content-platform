package com.yupi.template.storage;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.Path;import java.io.*;import javax.imageio.ImageIO;import java.awt.image.BufferedImage;import java.util.Random;import com.yupi.template.model.dto.image.ImageData;
class ImageResizeTest {
 @TempDir Path dir;
 @Test void jpegExpandingPastLimitIsBoundedlyResizedAndReadableAfterRestart()throws Exception{
  var image=new BufferedImage(2048,2048,BufferedImage.TYPE_INT_RGB);var random=new Random(7);for(int y=0;y<2048;y++)for(int x=0;x<2048;x++)image.setRGB(x,y,random.nextInt(0x1000000));
  var encoded=new ByteArrayOutputStream();ImageIO.write(image,"jpeg",encoded);assertTrue(encoded.size()<5242880);var decoded=ImageIO.read(new ByteArrayInputStream(encoded.toByteArray()));var expanded=new ByteArrayOutputStream();ImageIO.write(decoded,"png",expanded);assertTrue(expanded.size()>5242880);
  var storage=new LocalImageStorage(dir.toString(),5242880);var url=storage.save(ImageData.fromBytes(encoded.toByteArray(),"image/jpeg"),"ignored");var bytes=new LocalImageStorage(dir.toString(),5242880).read(url.substring(12));assertTrue(bytes.length<=5242880);var saved=ImageIO.read(new ByteArrayInputStream(bytes));assertNotNull(saved);assertTrue(saved.getWidth()<2048);assertEquals(saved.getWidth(),saved.getHeight());
 }
}
