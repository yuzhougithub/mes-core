package com.xihua.mes.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * A2: default mode is jwt; header mode starts only with the dev or test profile.
 */
class AuthModeGuardTest {

    private static ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AuthModeGuard.class)
                .withPropertyValues(properties);
    }

    @Test
    void defaultResolvesToJwt() {
        new ApplicationContextRunner()
                .withUserConfiguration(AuthModeGuard.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(AuthModeGuard.class).mode()).isEqualTo(PlantContextFilter.MODE_JWT);
                });

        runner().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getEnvironment().getProperty("spring.application.name")).isEqualTo("mes-api");
            assertThat(ctx.getEnvironment().getProperty("mes.auth.mode")).isEqualTo("jwt");
            assertThat(ctx.getBean(AuthModeGuard.class).mode()).isEqualTo(PlantContextFilter.MODE_JWT);
            assertThat(ctx.getEnvironment().getActiveProfiles()).isEmpty();
        });
    }

    @Test
    void headerWithDevProfileStarts() {
        runner("spring.profiles.active=dev").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getEnvironment().getActiveProfiles()).containsExactly("dev");
            assertThat(ctx.getBean(AuthModeGuard.class).mode()).isEqualTo(PlantContextFilter.MODE_HEADER);
        });
    }

    @Test
    void headerWithTestProfileStarts() {
        runner("spring.profiles.active=test").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getEnvironment().getActiveProfiles()).containsExactly("test");
            assertThat(ctx.getBean(AuthModeGuard.class).mode()).isEqualTo(PlantContextFilter.MODE_HEADER);
        });
    }

    @Test
    void headerWithStagingProfileFailsStartup() {
        assertHeaderRefused("spring.profiles.active=staging", "mes.auth.mode=header");
    }

    @Test
    void headerWithNoProfileFailsStartup() {
        assertHeaderRefused("mes.auth.mode=header");
    }

    private static void assertHeaderRefused(String... properties) {
        runner(properties).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(failureText(ctx.getStartupFailure()))
                    .contains(AuthModeGuard.HEADER_NOT_ALLOWED)
                    .contains("X-Plant-Id");
        });
    }

    private static String failureText(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
