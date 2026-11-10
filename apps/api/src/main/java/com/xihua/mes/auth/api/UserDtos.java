package com.xihua.mes.auth.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class UserDtos {

    private UserDtos() {
    }

    public record PlantBindingInput(
            UUID plantId,
            @JsonProperty("isDefault") Boolean defaultPlant,
            Boolean active,
            UUID workshopId,
            String jobTitle,
            List<UUID> roleIds) {
    }

    public record UserCreate(
            String username,
            String password,
            String code,
            String name,
            String mobile,
            String email,
            String remark,
            Boolean superAdmin,
            List<PlantBindingInput> plants) {
    }

    public record UserUpdate(
            String username,
            String name,
            String mobile,
            String email,
            String remark,
            Integer lockVersion) {
    }

    public record LockBody(Integer lockVersion) {
    }

    public record ResetPassword(Integer lockVersion, String newPassword) {
    }

    public record PlantReplace(Integer lockVersion, List<PlantBindingInput> plants) {
    }

    public record PlantBindingView(
            UUID plantId,
            String plantCode,
            String plantName,
            @JsonProperty("isDefault") boolean defaultPlant,
            boolean active,
            UUID workshopId,
            String jobTitle,
            List<UUID> roleIds,
            int lockVersion) {
    }

    public record PersonView(
            UUID id,
            String code,
            String name,
            String mobile,
            String email,
            boolean enabled,
            String remark,
            int lockVersion) {
    }

    public record UserSummary(
            UUID id,
            String username,
            String status,
            boolean superAdmin,
            boolean mustChangePassword,
            UUID personId,
            String personCode,
            String personName,
            UUID defaultPlantId,
            int lockVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record UserDetail(
            UUID id,
            String username,
            String status,
            boolean superAdmin,
            boolean mustChangePassword,
            int permVersion,
            UUID defaultPlantId,
            int lockVersion,
            PersonView person,
            List<PlantBindingView> plants,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String initialPassword) {
    }

    public record PasswordReset(
            @JsonInclude(JsonInclude.Include.NON_NULL) String password,
            boolean mustChangePassword) {
    }
}
