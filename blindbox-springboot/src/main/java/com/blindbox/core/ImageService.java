package com.blindbox.core;

import com.blindbox.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.UUID;

@Service
public class ImageService {
    private final Path directory;
    private final java.util.concurrent.Semaphore decodingSlots=new java.util.concurrent.Semaphore(2);
    public ImageService(@Value("${app.upload-dir:./uploads}") String dir) {this.directory=Path.of(dir).toAbsolutePath().normalize();}
    public String upload(MultipartFile file) {
        if(file.isEmpty() || file.getSize()>5*1024*1024) throw new BusinessException(400,"图片须在 1 字节至 5MB 之间");
        if(!decodingSlots.tryAcquire()) throw new BusinessException(503,"图片处理繁忙，请稍后重试");
        try(java.io.InputStream input=file.getInputStream();ImageInputStream stream=ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw new BusinessException(400,"文件不是可识别的图片");
            ImageReader reader=readers.next();
            BufferedImage image;
            try {
                String format=reader.getFormatName().toLowerCase();
                if(!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg"))
                    throw new BusinessException(400,"仅支持 PNG 和 JPEG 图片");
                reader.setInput(stream);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width>4096 || height>4096 || (long)width*height>12000000)
                    throw new BusinessException(400,"图片尺寸过大");
                image=reader.read(0);
            } finally {reader.dispose();}
            Files.createDirectories(directory);
            String filename=UUID.randomUUID()+".png";
            // Decode and re-encode to discard arbitrary file payloads and original filenames.
            if(!ImageIO.write(image,"png",directory.resolve(filename).toFile())) throw new IOException("Encoder unavailable");
            if(Files.size(directory.resolve(filename))>10*1024*1024) {
                Files.deleteIfExists(directory.resolve(filename));
                throw new BusinessException(400,"重编码后图片超过 10MB，请缩小图片");
            }
            return "/uploads/"+filename;
        } catch(IOException e) {throw new BusinessException(400,"图片读取或保存失败");}
        finally {decodingSlots.release();}
    }
}
