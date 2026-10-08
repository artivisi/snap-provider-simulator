package com.artivisi.snapsimulator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredEnvironmentTest {

    private static ApplicationEnvironmentPreparedEvent event(MockEnvironment environment) {
        return new ApplicationEnvironmentPreparedEvent(null, new SpringApplication(), new String[0], environment);
    }

    @Test
    @DisplayName("Lists every missing or blank variable")
    void listsMissing() {
        MockEnvironment environment = new MockEnvironment();
        RequiredEnvironment.VARIABLES.forEach(v -> environment.setProperty(v, "x"));
        environment.setProperty("SIMULATOR_OPERATOR_PASSWORD", " ");
        MockEnvironment partial = new MockEnvironment();
        RequiredEnvironment.VARIABLES.stream().filter(v -> !v.equals("SIMULATOR_DB_URL") && !v.equals("SIMULATOR_TIMESTAMP_SKEW"))
                .forEach(v -> partial.setProperty(v, "x"));
        assertThatThrownBy(() -> new RequiredEnvironment().onApplicationEvent(event(partial)))
                .hasMessage("Required environment variables not set: SIMULATOR_DB_URL, SIMULATOR_TIMESTAMP_SKEW");
        assertThat(RequiredEnvironment.missing(environment)).containsExactly("SIMULATOR_OPERATOR_PASSWORD");
    }

    @Test
    @DisplayName("Passes when all are set")
    void allSet() {
        MockEnvironment environment = new MockEnvironment();
        RequiredEnvironment.VARIABLES.forEach(v -> environment.setProperty(v, "x"));
        assertThatNoException().isThrownBy(() -> new RequiredEnvironment().onApplicationEvent(event(environment)));
    }
}
