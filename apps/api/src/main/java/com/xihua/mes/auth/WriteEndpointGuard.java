package com.xihua.mes.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fails startup when a write under {@code /api} has no {@link RequiresPerm}
 * and is not on {@link PermAllowlist}. Reads are enforced at runtime when annotated,
 * and do not fail startup.
 */
@Component
public class WriteEndpointGuard implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    public WriteEndpointGuard(ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.mappings = mappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<RequestMappingInfo, HandlerMethod> all = new LinkedHashMap<>();
        mappings.orderedStream().forEach(mapping -> all.putAll(mapping.getHandlerMethods()));
        verify(all);
    }

    public static void verify(Map<RequestMappingInfo, HandlerMethod> methods) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : methods.entrySet()) {
            String problem = problem(entry.getKey(), entry.getValue());
            if (problem != null) {
                missing.add(problem);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("写接口未标注 @RequiresPerm:\n" + String.join("\n", missing));
        }
    }

    public static Set<String> declaredPerms(Map<RequestMappingInfo, HandlerMethod> methods) {
        Set<String> codes = new TreeSet<>();
        codes.add(PermCodes.SHARED_WRITE);
        for (HandlerMethod method : methods.values()) {
            RequiresPerm perm = annotation(method);
            if (perm == null) {
                continue;
            }
            for (String code : perm.value()) {
                if (code != null && !code.isBlank()) {
                    codes.add(code.trim());
                }
            }
        }
        return codes;
    }

    static String problem(RequestMappingInfo info, HandlerMethod method) {
        Set<RequestMethod> http = info.getMethodsCondition().getMethods();
        boolean writes = http.isEmpty() || http.stream().anyMatch(WriteEndpointGuard::isWrite);
        if (!writes) {
            return null;
        }
        boolean annotated = annotated(method);
        List<String> offenders = new ArrayList<>();
        for (String path : patterns(info)) {
            if (!path.startsWith("/api")) {
                continue;
            }
            if (http.isEmpty()) {
                if (!annotated) {
                    offenders.add("ANY " + path);
                }
                continue;
            }
            for (RequestMethod verb : http) {
                if (!isWrite(verb) || PermAllowlist.allows(verb.name(), path) || annotated) {
                    continue;
                }
                offenders.add(verb.name() + " " + path);
            }
        }
        if (offenders.isEmpty()) {
            return null;
        }
        return String.join(", ", offenders) + " (" + method.getShortLogMessage() + ")";
    }

    private static boolean annotated(HandlerMethod method) {
        RequiresPerm perm = annotation(method);
        return perm != null && Arrays.stream(perm.value()).anyMatch(code -> code != null && !code.isBlank());
    }

    private static RequiresPerm annotation(HandlerMethod method) {
        RequiresPerm perm = method.getMethodAnnotation(RequiresPerm.class);
        if (perm != null) {
            return perm;
        }
        return method.getBeanType().getAnnotation(RequiresPerm.class);
    }

    private static boolean isWrite(RequestMethod method) {
        return method == RequestMethod.POST || method == RequestMethod.PUT
                || method == RequestMethod.PATCH || method == RequestMethod.DELETE;
    }

    private static List<String> patterns(RequestMappingInfo info) {
        List<String> paths = new ArrayList<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns().forEach(pattern -> paths.add(pattern.getPatternString()));
        }
        if (paths.isEmpty() && info.getPatternsCondition() != null) {
            paths.addAll(info.getPatternsCondition().getPatterns());
        }
        return paths;
    }
}
