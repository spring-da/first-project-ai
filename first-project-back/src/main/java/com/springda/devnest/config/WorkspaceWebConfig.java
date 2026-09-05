package com.springda.devnest.config;

import com.springda.devnest.admin.AdminWorkspaceAuditInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WorkspaceWebConfig implements WebMvcConfigurer {
    private final WorkspaceOwnerResolver resolver;
    private final AdminWorkspaceAuditInterceptor auditInterceptor;

    public WorkspaceWebConfig(WorkspaceOwnerResolver resolver, AdminWorkspaceAuditInterceptor auditInterceptor) {
        this.resolver = resolver;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/v1/**");
    }
}
