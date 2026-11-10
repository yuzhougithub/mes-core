package com.xihua.mes.auth.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record ChangePassword(String oldPassword, String newPassword) {
    }

    public record SwitchPlant(UUID plantId) {
    }

    public record PlantBrief(UUID id, String code, String name) {
    }

    public record TokenResponse(
            String accessToken,
            long expiresIn,
            boolean mustChangePassword,
            PlantBrief plant,
            List<String> roles) {
    }

    public record MeUser(
            String id,
            String username,
            String status,
            Boolean superAdmin,
            Boolean mustChangePassword,
            Integer permVersion) {
    }

    public record MePerson(UUID id, String code, String name) {
    }

    public record MePlant(String id, String code, String name) {
    }

    public record PlantOption(
            String id,
            String code,
            String name,
            @JsonProperty("isDefault") boolean defaultPlant) {
    }

    public record MeMenu(
            String id,
            @JsonInclude(JsonInclude.Include.NON_NULL) String parentId,
            String type,
            String name,
            @JsonInclude(JsonInclude.Include.NON_NULL) String permCode,
            @JsonInclude(JsonInclude.Include.NON_NULL) String path,
            @JsonInclude(JsonInclude.Include.NON_NULL) String component,
            @JsonInclude(JsonInclude.Include.NON_NULL) String icon,
            int sortNo,
            boolean visible,
            List<MeMenu> children) {
    }

    public record MeResponse(
            MeUser user,
            @JsonInclude(JsonInclude.Include.NON_NULL) MePerson person,
            @JsonInclude(JsonInclude.Include.NON_NULL) MePlant plant,
            List<PlantOption> plants,
            List<String> roles,
            List<String> permissions,
            List<MeMenu> menus) {
    }
}
