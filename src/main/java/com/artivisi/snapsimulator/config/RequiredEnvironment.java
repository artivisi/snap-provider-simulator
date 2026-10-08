package com.artivisi.snapsimulator.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Fails startup before any bean is created when a required environment
 * variable is missing, naming every missing one. There are no defaults.
 */
public class RequiredEnvironment implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    public static final List<String> VARIABLES = List.of(
            "SIMULATOR_DB_URL",
            "SIMULATOR_DB_USERNAME",
            "SIMULATOR_DB_PASSWORD",
            "SIMULATOR_TIMESTAMP_SKEW",
            "SIMULATOR_BANK_PRIVATE_KEY_PATH",
            "SIMULATOR_OPERATOR_USERNAME",
            "SIMULATOR_OPERATOR_PASSWORD",
            "SIMULATOR_OUTBOUND_CONNECT_TIMEOUT",
            "SIMULATOR_OUTBOUND_READ_TIMEOUT");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        List<String> missing = missing(event.getEnvironment());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Required environment variables not set: " + String.join(", ", missing));
        }
    }

    static List<String> missing(Environment environment) {
        return VARIABLES.stream().filter(name -> {
            String value = environment.getProperty(name);
            return value == null || value.isBlank();
        }).toList();
    }
}
