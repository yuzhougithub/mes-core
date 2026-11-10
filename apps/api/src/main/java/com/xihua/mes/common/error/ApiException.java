package com.xihua.mes.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Business error rendered as RFC 9457 Problem Details by
 * {@link com.xihua.mes.common.ApiExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String code, String detail, List<FieldError> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, what + " 不存在", List.of());
    }

    public static ApiException staleVersion() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.STALE_VERSION,
                "数据已被修改，请刷新后重试", List.of());
    }

    public static ApiException stateConflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.STATE_CONFLICT, detail, List.of());
    }

    public static ApiException duplicateCode(String code) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_CODE,
                "编码已存在：" + code,
                List.of(new FieldError("code", ErrorCodes.DUPLICATE_CODE, "编码已存在：" + code)));
    }

    public static ApiException inUse(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.IN_USE, detail, List.of());
    }

    public static ApiException plantForbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.PLANT_FORBIDDEN, detail, List.of());
    }

    public static ApiException unprocessable(String code, String detail, List<FieldError> errors) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail, errors);
    }

    public static ApiException validation(List<FieldError> errors) {
        return unprocessable(ErrorCodes.VALIDATION_FAILED, errors.size() + " 个字段不合法", errors);
    }

    public static ApiException passwordPolicy(List<FieldError> errors) {
        return unprocessable(ErrorCodes.PASSWORD_POLICY, "密码不符合策略", errors);
    }

    public static ApiException unauthenticated(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, detail, List.of());
    }

    public static ApiException tokenExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.TOKEN_EXPIRED, "登录已过期", List.of());
    }

    public static ApiException tokenInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.TOKEN_INVALID, "登录凭证无效", List.of());
    }

    public static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.BAD_CREDENTIALS, "用户名或密码错误", List.of());
    }

    public static ApiException accountLocked() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.ACCOUNT_LOCKED, "账号已锁定，请稍后再试", List.of());
    }

    public static ApiException accountDisabled() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.ACCOUNT_DISABLED, "账号已停用", List.of());
    }

    public static ApiException passwordChangeRequired() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.PASSWORD_CHANGE_REQUIRED, "请先修改密码", List.of());
    }

    public static ApiException noPlantAssignment() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.NO_PLANT_ASSIGNMENT, "没有可用的工厂任职", List.of());
    }

    public static ApiException permissionDenied(String permCode) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.PERMISSION_DENIED, "缺少权限点 " + permCode,
                List.of(new FieldError("permission", ErrorCodes.PERMISSION_DENIED, permCode)));
    }

    public static ApiException duplicatePermCode(String permCode) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_PERM_CODE,
                "权限点编码已存在：" + permCode,
                List.of(new FieldError("permCode", ErrorCodes.DUPLICATE_PERM_CODE, "权限点编码已存在：" + permCode)));
    }

    public static ApiException menuHasChildren() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.MENU_HAS_CHILDREN, "请先删除子节点", List.of());
    }

    public static ApiException menuInUse(List<String> roleCodes) {
        String joined = String.join(", ", roleCodes);
        List<FieldError> errors = roleCodes.stream()
                .map(code -> new FieldError("roles", ErrorCodes.MENU_IN_USE, code))
                .toList();
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.MENU_IN_USE, "菜单已被角色引用：" + joined, errors);
    }

    public static ApiException menuParentInvalid(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.MENU_PARENT_INVALID, detail, List.of());
    }

    public static ApiException deptNotEmpty() {
        return unprocessable(ErrorCodes.DEPT_NOT_EMPTY, "部门下仍有子部门或人员", List.of());
    }

    public static ApiException builtinProtected(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.BUILTIN_PROTECTED, detail, List.of());
    }

    public static ApiException plantMismatch() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.PLANT_MISMATCH,
                "X-Plant-Id 与当前工厂不一致", List.of());
    }
}
