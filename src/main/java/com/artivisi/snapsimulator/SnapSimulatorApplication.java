package com.artivisi.snapsimulator;

import com.artivisi.snapsimulator.config.RequiredEnvironment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SnapSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SnapSimulatorApplication.class);
        application.addListeners(new RequiredEnvironment());
        application.run(args);
    }
}
