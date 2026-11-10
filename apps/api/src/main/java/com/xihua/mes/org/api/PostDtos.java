package com.xihua.mes.org.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.xihua.mes.org.domain.Post;

public final class PostDtos {

    private PostDtos() {
    }

    public record PostWrite(
            String code,
            String name,
            Integer sortNo,
            Boolean enabled,
            Integer lockVersion) {
    }

    public record PostView(
            UUID id,
            String code,
            String name,
            int sortNo,
            boolean enabled,
            int lockVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static PostView of(Post row) {
            return new PostView(
                    row.getId(),
                    row.getCode(),
                    row.getName(),
                    row.getSortNo() == null ? 0 : row.getSortNo(),
                    Boolean.TRUE.equals(row.getEnabled()),
                    row.getLockVersion() == null ? 0 : row.getLockVersion(),
                    row.getCreatedAt(),
                    row.getUpdatedAt());
        }
    }
}
