package com.yupi.template.service;

import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.dto.image.ImageData;
import com.yupi.template.model.dto.image.ImageRequest;
import com.yupi.template.model.enums.ImageMethodEnum;
import com.yupi.template.storage.ImageStorage;
import com.yupi.template.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Explicit no-model demo. Remote photos are decorative placeholders, never AI output. */
@Service
public class DemoImageService {
    private final ImageStorage storage;
    private final LocalImageStorage validator;
    private final boolean remoteEnabled;
    public DemoImageService(ImageStorage storage, LocalImageStorage validator,
            @Value("${article.images.demo.remote-enabled:true}") boolean remoteEnabled) {
        this.storage=storage;this.validator=validator;this.remoteEnabled=remoteEnabled;
    }
    public static boolean selected(List<String> methods) { return methods != null && methods.equals(List.of("DEMO")); }
    public static List<ArticleState.ImageRequirement> plan() {
        var req=new ArticleState.ImageRequirement();req.setImageSource("DEMO");req.setPosition(1);req.setType("cover");
        req.setSectionTitle("演示/占位（非AI生图）");req.setPlaceholderId("{{DEMO_IMAGE}}");return List.of(req);
    }
    public ImageServiceStrategy.ImageResult create(ImageRequest request) {
        com.yupi.template.runtime.RuntimeScope.guard();
        byte[] bytes=null;ImageMethodEnum source=ImageMethodEnum.DEMO_PNG;
        if(remoteEnabled)try {bytes=validator.normalize(ImageData.fromBytes(download(),"image/jpeg"));source=ImageMethodEnum.PICSUM;}catch(IOException | com.yupi.template.storage.ImageStorageException ignored) { /* Bounded offline fallback, no model call. */ }
        if(bytes==null)bytes=placeholder();
        com.yupi.template.runtime.RuntimeScope.guard();
        // Storage errors propagate separately; they must not trigger another remote/model request here.
        return new ImageServiceStrategy.ImageResult(storage.save(ImageData.fromBytes(bytes,"image/png"),"demo"),source);
    }
    static void validateUri(URI uri, boolean redirected) throws IOException {
        String expected=redirected?"fastly.picsum.photos":"picsum.photos";
        if(!"https".equals(uri.getScheme()) || !expected.equals(uri.getHost()) || uri.getUserInfo()!=null || (uri.getPort()!=-1&&uri.getPort()!=443) || uri.getFragment()!=null)throw new IOException("Untrusted demo image URL");
        if(redirected&&!uri.getPath().matches("/id/[0-9]+/800/600\\.jpg"))throw new IOException("Unexpected demo image path");
        if(!redirected&&!"/800/600".equals(uri.getPath()))throw new IOException("Unexpected demo image path");
    }
    protected byte[] download() throws IOException {
        URI uri=URI.create("https://picsum.photos/800/600");
        for(int hop=0;hop<2;hop++) {
            validateUri(uri,hop==1);
            for(var address:InetAddress.getAllByName(uri.getHost())) {
                byte[] raw=address.getAddress();
                if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||(raw.length==16&&(raw[0]&0xfe)==0xfc))throw new IOException("Non-public demo host");
            }
            var c=(HttpURLConnection)uri.toURL().openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(3000);c.setReadTimeout(3000);
            try {
                int code=c.getResponseCode();
                if(hop==0 && (code==301||code==302||code==303||code==307||code==308)) {
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException();
                    try {uri=URI.create(location);}catch(IllegalArgumentException e){throw new IOException(e);}continue;
                }
                if(code!=200 || c.getContentLengthLong()>5242880)throw new IOException("Demo image unavailable");
                try(var in=c.getInputStream()){byte[] data=in.readNBytes(5242881);if(data.length>5242880)throw new IOException("Demo image too large");return data;}
            }finally {c.disconnect();}
        }
        throw new IOException("Redirect limit reached");
    }
    public static byte[] placeholder() {
        try {
            var image=new BufferedImage(800,600,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
            g.setColor(new Color(231,239,234));g.fillRect(0,0,800,600);
            g.setColor(new Color(48,87,66));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,34));g.drawString("DEMO / PLACEHOLDER",155,265);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,22));g.drawString("Program-drawn PNG - not AI generated",180,320);
            g.drawString("No semantic match guaranteed",225,360);g.dispose();
            var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Cannot draw demo PNG",e);}
    }
}
