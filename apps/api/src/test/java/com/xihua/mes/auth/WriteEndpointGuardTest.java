package com.xihua.mes.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** Startup rule: a write under {@code /api} without {@link RequiresPerm} aborts the context. */
class WriteEndpointGuardTest {

    @Test
    void startupFailsWhenAWriteEndpointIsUnannotated() throws Exception {
        HandlerMethod method = handler(Unguarded.class, "create");
        RequestMappingInfo info = RequestMappingInfo.paths("/api/v1/unguarded").methods(RequestMethod.POST).build();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> WriteEndpointGuard.verify(Map.of(info, method)));
        assertThat(ex.getMessage()).contains("POST /api/v1/unguarded");
    }

    @Test
    void allowlistedAuthWritesReadsAndAnnotatedWritesPass() throws Exception {
        WriteEndpointGuard.verify(Map.of(
                RequestMappingInfo.paths("/api/v1/auth/login").methods(RequestMethod.POST).build(),
                handler(Unguarded.class, "login"),
                RequestMappingInfo.paths("/api/v1/auth/switch-plant").methods(RequestMethod.POST).build(),
                handler(Unguarded.class, "login"),
                RequestMappingInfo.paths("/api/v1/menus").methods(RequestMethod.GET).build(),
                handler(Unguarded.class, "read"),
                RequestMappingInfo.paths("/api/v1/menus").methods(RequestMethod.POST).build(),
                handler(Guarded.class, "create")));
    }

    private static HandlerMethod handler(Class<?> type, String name)
            throws NoSuchMethodException, ReflectiveOperationException {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getDeclaredMethod(name));
    }

    public static class Unguarded {
        public void create() {
        }

        public void login() {
        }

        public void read() {
        }
    }

    public static class Guarded {
        @RequiresPerm("sys:menu:create")
        public void create() {
        }
    }
}
