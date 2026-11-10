package com.xihua.mes.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.xihua.mes.auth.infra.PermissionMapper;

/** Warns when {@link RequiresPerm} names a code that is not in {@code sys_menu}. Does not fail startup. */
@Component
public class PermCodeCatalogCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermCodeCatalogCheck.class);

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final PermissionMapper permissions;

    public PermCodeCatalogCheck(
            ObjectProvider<RequestMappingHandlerMapping> mappings,
            PermissionMapper permissions) {
        this.mappings = mappings;
        this.permissions = permissions;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<RequestMappingInfo, HandlerMethod> all = new LinkedHashMap<>();
        mappings.orderedStream().forEach(mapping -> all.putAll(mapping.getHandlerMethods()));
        Set<String> missing = new TreeSet<>(WriteEndpointGuard.declaredPerms(all));
        missing.removeAll(new TreeSet<>(permissions.storedPermCodes()));
        if (!missing.isEmpty()) {
            log.warn("RequiresPerm codes missing from sys_menu (menus may have been deleted): {}", missing);
        }
    }
}
