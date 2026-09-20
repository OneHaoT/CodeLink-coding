package com.codeknest.server.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源映射：
 *   /files/** → 本地上传文件（头像等）
 *   /image/** → 文章图片（临时 / 正式）
 */
@Configuration
@EnableScheduling
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${codeknest.upload.local-path}")
    private String localPath;

    @Value("${codeknest.upload.image-path:./image}")
    private String imagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations(toFileUrl(localPath));

        registry.addResourceHandler("/image/**")
                .addResourceLocations(toFileUrl(imagePath));
    }

    private static String toFileUrl(String path) {
        String location = Paths.get(path).toAbsolutePath().normalize().toString().replace('\\', '/');
        if (!location.endsWith("/")) {
            location += "/";
        }
        return "file:///" + location;
    }
}
