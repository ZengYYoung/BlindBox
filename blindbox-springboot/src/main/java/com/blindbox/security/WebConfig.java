package com.blindbox.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String location;
    public WebConfig(@Value("${app.upload-dir:./uploads}") String dir) {
        location=Path.of(dir).toAbsolutePath().normalize().toUri().toString();
    }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(location.endsWith("/")?location:location+"/");
    }
}
