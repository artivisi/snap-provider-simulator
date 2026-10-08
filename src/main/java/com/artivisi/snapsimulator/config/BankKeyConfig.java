package com.artivisi.snapsimulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class BankKeyConfig {

    @Bean
    BankKeys bankKeys(SimulatorProperties properties) {
        return BankKeys.load(Path.of(properties.bank().keyDir()));
    }
}
