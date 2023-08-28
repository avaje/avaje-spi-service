package io.avaje.spi.internal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleReaderTest {

    @Test
    void singleMatch_when_fullyQualified() {
       var services = new HashMap<String, Set<String>>();
       services.put("javax.annotation.processing.Processor", Set.of("io.avaje.spi.internal.ServiceProcessor"));

       var mr = new ModuleReader(services);
       mr.readLine("provides javax.annotation.processing.Processor with io.avaje.spi.internal.ServiceProcessor;");
       var missing = mr.missing();

       assertThat(missing.get("Processor")).isEmpty();
    }

    @Test
    void singleMatch_when_bothShortNames() {
        var services = new HashMap<String, Set<String>>();
        services.put("javax.annotation.processing.Processor", Set.of("io.avaje.spi.internal.ServiceProcessor"));

        var mr = new ModuleReader(services);
        mr.readLine("import javax.annotation.processing.Processor;");
        mr.readLine("import io.avaje.spi.internal.ServiceProcessor;");
        mr.readLine("provides Processor with ServiceProcessor;");
        var missing = mr.missing();

        assertThat(missing.get("Processor")).isEmpty();
    }

    @Test
    void singleMatch_when_shortNameInterface() {
        var services = new HashMap<String, Set<String>>();
        services.put("javax.annotation.processing.Processor", Set.of("io.avaje.spi.internal.ServiceProcessor"));

        var mr = new ModuleReader(services);
        mr.readLine("import javax.annotation.processing.Processor;");
        mr.readLine("provides Processor with io.avaje.spi.internal.ServiceProcessor;");
        var missing = mr.missing();

        assertThat(missing.get("Processor")).isEmpty();
    }

    @Test
    void singleMatch_when_shortNameImplementation() {
        var services = new HashMap<String, Set<String>>();
        services.put("javax.annotation.processing.Processor", Set.of("io.avaje.spi.internal.ServiceProcessor"));

        var mr = new ModuleReader(services);
        mr.readLine("import io.avaje.spi.internal.ServiceProcessor;");
        mr.readLine("provides javax.annotation.processing.Processor with ServiceProcessor;");
        var missing = mr.missing();

        assertThat(missing.get("Processor")).isEmpty();
    }
}