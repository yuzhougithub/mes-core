package com.xihua.mes.auth.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.auth.AuthService;
import com.xihua.mes.auth.AuthService.IssuedSession;
import com.xihua.mes.auth.ClientInfo;
import com.xihua.mes.auth.CsrfGuard;
import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.auth.PlantContextFilter;
import com.xihua.mes.auth.RefreshCookies;
import com.xihua.mes.auth.api.AuthDtos.ChangePassword;
import com.xihua.mes.auth.api.AuthDtos.LoginRequest;
import com.xihua.mes.auth.api.AuthDtos.MePlant;
import com.xihua.mes.auth.api.AuthDtos.MeResponse;
import com.xihua.mes.auth.api.AuthDtos.MeUser;
import com.xihua.mes.auth.api.AuthDtos.PlantOption;
import com.xihua.mes.auth.api.AuthDtos.SwitchPlant;
import com.xihua.mes.auth.api.AuthDtos.TokenResponse;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final boolean headerMode;

    public AuthController(AuthService auth, @Value("${mes.auth.mode:jwt}") String mode) {
        this.auth = auth;
        this.headerMode = PlantContextFilter.MODE_HEADER.equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody(required = false) LoginRequest req, HttpServletRequest request,
            HttpServletResponse response) {
        if (req == null) {
            throw badRequest();
        }
        IssuedSession session = auth.login(req.username(), req.password(), ClientInfo.from(request));
        writeRefresh(response, session);
        return session.body();
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        CsrfGuard.check(request);
        IssuedSession session = auth.refresh(RefreshCookies.read(request), ClientInfo.from(request));
        writeRefresh(response, session);
        return session.body();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        CsrfGuard.check(request);
        auth.logout(RefreshCookies.read(request), bearer(request));
        response.addHeader(HttpHeaders.SET_COOKIE, RefreshCookies.clear().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/switch-plant")
    public TokenResponse switchPlant(@RequestBody(required = false) SwitchPlant req, HttpServletRequest request) {
        if (req == null) {
            throw badRequest();
        }
        return auth.switchPlant(CurrentContext.userUuid(), req.plantId(), RefreshCookies.read(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody(required = false) ChangePassword req) {
        if (req == null) {
            throw badRequest();
        }
        auth.changePassword(CurrentContext.userUuid(), req.oldPassword(), req.newPassword(), CurrentContext.jti());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        if (headerMode) {
            return headerMe(request);
        }
        return auth.me(CurrentContext.userUuid(), CurrentContext.requirePlantUuid());
    }

    private static MeResponse headerMe(HttpServletRequest request) {
        String user = blankToNull(request.getHeader(PlantContextFilter.HEADER_USER_ID));
        String plant = blankToNull(request.getHeader(PlantContextFilter.HEADER_PLANT_ID));
        MePlant mePlant = plant == null ? null : new MePlant(plant, null, null);
        List<PlantOption> plants = mePlant == null
                ? List.of()
                : List.of(new PlantOption(mePlant.id(), null, null, true));
        return new MeResponse(new MeUser(user, null, null, null, null, null), null, mePlant, plants,
                List.of("ADMIN"), List.of(), List.of());
    }

    private static void writeRefresh(HttpServletResponse response, IssuedSession session) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                RefreshCookies.issue(session.refreshToken(), session.refreshMaxAge()).toString());
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ApiException badRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                List.of());
    }
}
