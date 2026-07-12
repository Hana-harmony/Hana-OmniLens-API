package com.hana.omnilens.portal.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omnilens.common.api.ApiResponse;
import com.hana.omnilens.portal.PortalAccountService;
import com.hana.omnilens.portal.PortalAccountService.PortalSession;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/portal/auth")
@Tag(name = "Portal authentication", description = "Hana OmniLens portal member authentication")
public class PortalAuthController {

    private final PortalAccountService accountService;

    public PortalAuthController(PortalAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Create a Hana OmniLens portal member account")
    public ApiResponse<PortalSessionResponse> signUp(@Valid @RequestBody PortalSignUpRequest request) {
        return ApiResponse.success(PortalSessionResponse.from(accountService.signUp(
                request.username(), request.password(), request.passwordConfirmation(), request.name(), request.phoneNumber())));
    }

    @PostMapping("/login")
    @Operation(summary = "Create a Hana OmniLens portal session")
    public ApiResponse<PortalSessionResponse> login(@Valid @RequestBody PortalLoginRequest request) {
        return ApiResponse.success(PortalSessionResponse.from(accountService.login(request.username(), request.password())));
    }

    public record PortalSignUpRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,64}") String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(min = 12, max = 128) String passwordConfirmation,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "[0-9+() -]{7,30}") String phoneNumber
    ) {
    }

    public record PortalLoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {
    }

    public record PortalSessionResponse(String accessToken, String tokenType, java.time.Instant expiresAt, boolean passwordChangeRequired, PortalUserResponse user) {
        static PortalSessionResponse from(PortalSession session) {
            return new PortalSessionResponse(session.accessToken(), "Bearer", session.expiresAt(), session.user().passwordChangeRequired(), PortalUserResponse.from(session.user()));
        }
    }

    public record PortalUserResponse(String userId, String username, String name, String phoneNumber, String role) {
        static PortalUserResponse from(com.hana.omnilens.portal.PortalUser user) {
            return new PortalUserResponse(user.userId(), user.username(), user.displayName(), user.phoneNumber(), user.role().name());
        }
    }
}
