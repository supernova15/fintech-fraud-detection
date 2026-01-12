package org.fintech.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuntimeMetricsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            MetricsAutoConfiguration.class,
            SimpleMetricsExportAutoConfiguration.class
        ))
        .withUserConfiguration(RuntimeMetricsConfig.class);

    @Test
    void registersCpuAndMemoryMeters() {
        contextRunner.run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.find("jvm.memory.used").meter()).isNotNull();
            boolean hasCpuUsage = registry.find("process.cpu.usage").meter() != null
                || registry.find("system.cpu.usage").meter() != null;
            assertThat(hasCpuUsage).isTrue();
        });
    }
}
