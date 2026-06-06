package com.synchplay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves uploaded native video files + thumbnails under {@code /media/**}.
 *
 * Spring's {@code ResourceHttpRequestHandler} supports HTTP Range requests out
 * of the box, so the HTML5 {@code <video>} player can stream and seek. This
 * path sits outside {@code /api/**}, so SecurityConfig leaves it public — fine
 * for a local demo (no auth header needed on a {@code <video src>}).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadsDir;

    public WebConfig(@Value("${synchplay.uploads.dir}") String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path abs = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(abs);   // ensure it exists so the location resolves as a directory
        } catch (IOException ignored) {
            // if creation fails, uploads will fail later with a clear 500; nothing to do here
        }
        // Trailing slash is required for the resource handler to treat the location as a directory.
        String location = abs.toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location);
    }
}
