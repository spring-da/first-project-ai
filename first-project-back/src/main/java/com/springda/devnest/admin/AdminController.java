package com.springda.devnest.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminWorkspaceService workspaceService;

    public AdminController(AdminService adminService, AdminWorkspaceService workspaceService) {
        this.adminService = adminService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/accounts")
    List<AdminDtos.AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return adminService.listAccounts(jwt.getSubject());
    }

    @PostMapping("/invitations")
    ResponseEntity<AdminDtos.InvitationSecretResponse> invite(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminDtos.InviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(adminService.invite(jwt.getSubject(), request));
    }

    @PostMapping("/invitations/{invitationId}/rotate-token")
    ResponseEntity<AdminDtos.InvitationSecretResponse> rotateInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String invitationId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminService.rotateInvitation(jwt.getSubject(), invitationId));
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String invitationId
    ) {
        adminService.revokeInvitation(jwt.getSubject(), invitationId);
    }

    @PatchMapping("/accounts/{userId}/status")
    AdminDtos.AccountResponse setEnabled(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody AdminDtos.StatusRequest request
    ) {
        return adminService.setEnabled(jwt.getSubject(), userId, request.enabled());
    }

    @PostMapping("/accounts/{userId}/reset-password")
    ResponseEntity<AdminDtos.TemporaryPasswordResponse> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminService.resetPassword(jwt.getSubject(), userId));
    }

    @DeleteMapping("/accounts/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        adminService.deleteAccount(jwt.getSubject(), userId);
    }

    @GetMapping("/accounts/{userId}/workspace/account")
    ResponseEntity<AdminWorkspaceDtos.Account> workspaceAccount(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String userId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(workspaceService.memberAccount(jwt.getSubject(), userId));
    }

    @GetMapping("/accounts/{userId}/workspace")
    AdminWorkspaceDtos.Snapshot workspace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        return workspaceService.snapshot(jwt.getSubject(), userId);
    }

    @GetMapping("/accounts/{userId}/workspace/markdown-documents/{documentId}")
    com.springda.devnest.markdown.MarkdownDocumentDtos.Response workspaceMarkdownDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @PathVariable String documentId
    ) {
        return workspaceService.markdownDocument(jwt.getSubject(), userId, documentId);
    }

    @GetMapping("/accounts/{userId}/workspace/markdown-images/{imageId}")
    ResponseEntity<StreamingResponseBody> workspaceMarkdownImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @PathVariable String imageId
    ) {
        var image = workspaceService.markdownImage(jwt.getSubject(), userId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.size())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.writer()::writeTo);
    }
}
