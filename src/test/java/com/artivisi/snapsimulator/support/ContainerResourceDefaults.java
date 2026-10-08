package com.artivisi.snapsimulator.support;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.testcontainers.core.CreateContainerCmdModifier;

/**
 * Explicit memory and CPU limits for every Testcontainers container. Apple
 * Container (via socktainer) reserves 1 GiB / 4 CPUs per container when no
 * limit is set; Docker Engine is unaffected. Registered in META-INF/services so
 * it also covers Ryuk. Remove once socktainer applies its own default.
 */
public class ContainerResourceDefaults implements CreateContainerCmdModifier {

    private static final long MEGABYTE = 1024L * 1024L;
    private static final long NANO_CPUS_PER_CPU = 1_000_000_000L;

    @Override
    public CreateContainerCmd modify(CreateContainerCmd cmd) {
        boolean helper = cmd.getImage() != null && cmd.getImage().contains("testcontainers/ryuk");
        cmd.getHostConfig()
                .withMemory((helper ? 256L : 512L) * MEGABYTE)
                .withNanoCPUs((helper ? 1L : 2L) * NANO_CPUS_PER_CPU);
        return cmd;
    }
}
