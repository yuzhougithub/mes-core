package com.xihua.mes.auth.api;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xihua.mes.auth.UserService;
import com.xihua.mes.auth.api.UserDtos.LockBody;
import com.xihua.mes.auth.api.UserDtos.PasswordReset;
import com.xihua.mes.auth.api.UserDtos.PlantReplace;
import com.xihua.mes.auth.api.UserDtos.ResetPassword;
import com.xihua.mes.auth.api.UserDtos.UserCreate;
import com.xihua.mes.auth.api.UserDtos.UserDetail;
import com.xihua.mes.auth.api.UserDtos.UserSummary;
import com.xihua.mes.auth.api.UserDtos.UserUpdate;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.web.PageQuery;
import com.xihua.mes.common.web.PageResult;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResult<UserSummary> list(HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        return users.list(PageQuery.from(request), q, status);
    }

    @PostMapping
    public ResponseEntity<UserDetail> create(@RequestBody(required = false) UserCreate req) {
        UserDetail created = users.create(req);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public UserDetail get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PutMapping("/{id}")
    public UserDetail update(@PathVariable UUID id, @RequestBody(required = false) UserUpdate req) {
        return users.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) Integer lockVersion) {
        users.delete(id, lockVersion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public UserDetail enable(@PathVariable UUID id, @RequestBody(required = false) LockBody req) {
        return users.setStatus(id, lock(req), "ACTIVE");
    }

    @PostMapping("/{id}/disable")
    public UserDetail disable(@PathVariable UUID id, @RequestBody(required = false) LockBody req) {
        return users.setStatus(id, lock(req), "DISABLED");
    }

    @PostMapping("/{id}/unlock")
    public UserDetail unlock(@PathVariable UUID id, @RequestBody(required = false) LockBody req) {
        return users.unlock(id, lock(req));
    }

    @PostMapping("/{id}/reset-password")
    public PasswordReset resetPassword(@PathVariable UUID id, @RequestBody(required = false) ResetPassword req) {
        return users.resetPassword(id, req);
    }

    @PutMapping("/{id}/plants")
    public UserDetail replacePlants(@PathVariable UUID id, @RequestBody(required = false) PlantReplace req) {
        return users.replacePlants(id, req);
    }

    private static Integer lock(LockBody req) {
        if (req == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求体不是合法 JSON 或字段类型不符",
                    java.util.List.of());
        }
        return req.lockVersion();
    }
}
