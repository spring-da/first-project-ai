package com.springda.devnest.config;

import tools.jackson.databind.ObjectMapper;
import com.springda.devnest.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;

@Component
public class AccountSecurityFilter extends OncePerRequestFilter {

    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final UserRepository users;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public AccountSecurityFilter(UserRepository users, AppProperties properties, ObjectMapper objectMapper) {
        this.users = users;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !properties.security().issuer().equals(jwtAuthentication.getToken().getClaimAsString("iss"))) {
            filterChain.doFilter(request, response);
            return;
        }

        var user = users.findById(jwtAuthentication.getToken().getSubject()).orElse(null);
        if (user == null || !user.isEnabled()) {
            SecurityContextHolder.clearContext();
            writeProblem(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "登录状态已失效，请重新登录", "ACCOUNT_UNAVAILABLE");
            return;
        }

        var tokenVersionClaim = jwtAuthentication.getToken().getClaim("auth_version");
        if (!(tokenVersionClaim instanceof Number tokenVersion)
                || tokenVersion.intValue() != user.getAuthVersion()) {
            SecurityContextHolder.clearContext();
            writeProblem(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "登录状态已失效，请重新登录", "SESSION_REVOKED");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwtAuthentication.getToken(), user.getAuthorities(), jwtAuthentication.getToken().getSubject()));

        if (user.isTemporaryPasswordExpired(Instant.now())) {
            SecurityContextHolder.clearContext();
            writeProblem(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized", "临时密码已过期，请联系管理员重新重置", "TEMPORARY_PASSWORD_EXPIRED");
            return;
        }

        if (user.isForcePasswordChange() && !isPasswordRecoveryRequest(request)) {
            writeProblem(response, request, HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden", "管理员已重置你的密码，请先设置新密码", PASSWORD_CHANGE_REQUIRED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPasswordRecoveryRequest(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && "/api/v1/auth/me".equals(path))
                || ("PUT".equals(request.getMethod()) && "/api/v1/auth/password".equals(path));
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String title,
            String detail,
            String code
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
