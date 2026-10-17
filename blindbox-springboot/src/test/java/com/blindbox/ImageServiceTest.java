package com.blindbox;
import com.blindbox.core.ImageService;
import com.blindbox.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageServiceTest {
    @TempDir Path directory;
    @Test void rejectsTextRenamedAsPng() {
        ImageService service=new ImageService(directory.toString());
        assertThrows(BusinessException.class,()->service.upload(new MockMultipartFile("file","fake.png","image/png","bad".getBytes())));
    }
    @Test void reencodesImageAndIgnoresOriginalPath() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(5,5,BufferedImage.TYPE_INT_RGB),"png",bytes);
        String url=new ImageService(directory.toString()).upload(new MockMultipartFile("file","../../bad.html","text/html",bytes.toByteArray()));
        assertTrue(url.matches("/uploads/[a-f0-9-]+[.]png"));
        assertNotNull(ImageIO.read(directory.resolve(url.substring("/uploads/".length())).toFile()));
    }
}
