package com.springda.devnest.config;

import com.springda.devnest.common.BadRequestException;
import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Applies only to business controllers, never to login, passwords or account administration. */
@Component
public class WorkspaceOwnerResolver implements HandlerMethodArgumentResolver {
    public static final String HEADER = "X-Workspace-Owner";
    private final UserRepository users;

    public WorkspaceOwnerResolver(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(WorkspaceOwner.class)
                && parameter.getParameterType() == String.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        if (!(request.getUserPrincipal() instanceof JwtAuthenticationToken authentication)) {
            throw new ForbiddenException("请先登录");
        }
        var actorId = authentication.getToken().getSubject();
        var targetIds = request.getHeaderValues(HEADER);
        if (targetIds == null) return actorId;

        // Reload privileges instead of trusting either the supplied header or an old role claim.
        var actor = users.findById(actorId).orElseThrow(() -> new ForbiddenException("需要管理员权限"));
        if (!actor.isEnabled() || actor.getRole() != UserRole.ADMIN || actor.isForcePasswordChange()) {
            throw new ForbiddenException("需要管理员权限");
        }
        if (targetIds.length != 1 || !targetIds[0].matches("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")) {
            throw new BadRequestException("工作区账户标识无效");
        }
        // Disabled members can still be maintained by an active administrator.
        return users.findById(targetIds[0]).orElseThrow(() -> new NotFoundException("账户", targetIds[0])).getId();
    }
}
