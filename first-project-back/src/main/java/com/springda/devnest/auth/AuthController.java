package com.springda.devnest.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(authService.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @GetMapping("/me")
    ResponseEntity<AuthDtos.UserSummary> currentUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.currentUser(jwt.getSubject()));
    }

    @PutMapping("/password")
    ResponseEntity<AuthDtos.AuthResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.changePassword(jwt.getSubject(), request));
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        authService.logoutAll(jwt.getSubject());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
