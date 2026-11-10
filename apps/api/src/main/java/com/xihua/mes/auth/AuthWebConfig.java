package com.xihua.mes.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link PlantAccessInterceptor} when a {@link PlantDirectory} is available (not in thin MVC slices). */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<PlantDirectory> plantDirectory;

    public AuthWebConfig(ObjectProvider<PlantDirectory> plantDirectory) {
        this.plantDirectory = plantDirectory;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        PlantDirectory directory = plantDirectory.getIfAvailable();
        if (directory == null) {
            return;
        }
        registry.addInterceptor(new PlantAccessInterceptor(directory))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/plants",
                        "/api/v1/plants/**",
                        "/api/v1/auth",
                        "/api/v1/auth/**");
    }
}
