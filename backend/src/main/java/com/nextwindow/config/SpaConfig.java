package com.nextwindow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the bundled React app when the frontend build is packaged into {@code classpath:/static}
 * (single-service deployments). Unknown non-API paths fall back to index.html for client-side routing.
 * Without a bundled frontend (e.g. docker-compose with nginx) this has no effect.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {
    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource r = location.createRelative(path);
                        if (r.exists() && r.isReadable()) return r;
                        if (path.startsWith("api/") || path.startsWith("actuator/") || !INDEX.exists()) return null;
                        return path.contains(".") ? null : INDEX;
                    }
                });
    }
}
