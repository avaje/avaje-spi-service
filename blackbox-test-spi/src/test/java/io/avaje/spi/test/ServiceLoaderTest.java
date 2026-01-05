package io.avaje.spi.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import io.avaje.inject.spi.InjectExtension;
import io.avaje.spi.test.SPIInterface.NestedSPIInterface;

class ServiceLoaderTest {

  @Test
  void testSPIInterfaceServicesAreLoaded() {
    ServiceLoader<SPIInterface> loader = ServiceLoader.load(SPIInterface.class);
    List<SPIInterface> services =
        StreamSupport.stream(loader.spliterator(), false).collect(Collectors.toList());

    assertFalse(services.isEmpty(), "SPIInterface services should be loaded");

    List<String> serviceClassNames =
        services.stream().map(s -> s.getClass().getSimpleName()).collect(Collectors.toList());

    // Verify expected service implementations are loaded
    assertTrue(
        serviceClassNames.contains("CommonClass"),
        "CommonClass should be loaded as SPIInterface service");
    assertTrue(
        serviceClassNames.contains("CommonClass2"),
        "CommonClass2 should be loaded as SPIInterface service");
    assertTrue(
        serviceClassNames.contains("CommonClass3"),
        "CommonClass3 should be loaded as SPIInterface service");
    assertTrue(
        serviceClassNames.contains("DefaultSPIInterface"),
        "DefaultSPIInterface should be loaded as SPIInterface service");
    assertTrue(
        serviceClassNames.contains("ManualSPI"),
        "ManualSPI should be loaded as SPIInterface service");

    // method provided
    assertTrue(
        serviceClassNames.contains("CommonClass4"),
        "CommonClass4 should be loaded as SPIInterface service");
    assertTrue(
        serviceClassNames.contains("CommonClass5"),
        "CommonClass5 should be loaded as SPIInterface service");
  }

  @Test
  void testNestedSPIInterfaceServicesAreLoaded() {
    ServiceLoader<NestedSPIInterface> loader = ServiceLoader.load(NestedSPIInterface.class);
    List<NestedSPIInterface> services =
        StreamSupport.stream(loader.spliterator(), false).collect(Collectors.toList());

    assertFalse(services.isEmpty(), "NestedSPIInterface services should be loaded");

    List<String> serviceClassNames =
        services.stream().map(s -> s.getClass().getSimpleName()).collect(Collectors.toList());

    assertTrue(
        serviceClassNames.contains("CommonClass2"),
        "CommonClass2 should be loaded as NestedSPIInterface service");
    assertTrue(
        serviceClassNames.contains("DefaultNested$$SPIInterface"),
        "DefaultNested$$SPIInterface should be loaded as NestedSPIInterface service");
  }

  @Test
  void testInjectPluginServicesAreLoaded() {
    ServiceLoader<InjectExtension> loader = ServiceLoader.load(InjectExtension.class);
    List<InjectExtension> services =
        StreamSupport.stream(loader.spliterator(), false).collect(Collectors.toList());

    assertFalse(services.isEmpty(), "InjectPlugin services should be loaded");

    List<String> serviceClassNames =
        services.stream().map(s -> s.getClass().getSimpleName()).collect(Collectors.toList());

    assertTrue(
        serviceClassNames.contains("InjectProvider"),
        "InjectProvider should be loaded as InjectPlugin service");
  }
}
