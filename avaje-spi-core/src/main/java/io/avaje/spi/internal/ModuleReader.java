package io.avaje.spi.internal;

import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ModuleElement;

import io.avaje.prism.GenerateModuleInfoReader;
import io.avaje.spi.internal.ModuleInfoReader.Provides;

@GenerateModuleInfoReader
public final class ModuleReader {
  private final Map<String, Set<String>> missingServicesMap = new HashMap<>();

  private boolean staticWarning;

  private boolean coreWarning;

  public ModuleReader(Map<String, Set<String>> services) {
    services.forEach(this::add);
  }

  private void add(String k, Set<String> v) {
    missingServicesMap.put(k.replace("$", "."), v);
  }

  public void read(BufferedReader reader, ModuleElement element) throws IOException {

    var module = new ModuleInfoReader(element, reader);

    for (var require : module.requires()) {
      var dep = require.getDependency();
      if (!require.isStatic() && dep.getQualifiedName().contentEquals("io.avaje.spi")) {
        staticWarning = true;
      }
      if (dep.getQualifiedName().contentEquals("io.avaje.spi.core")) {
        coreWarning = true;
      }
      if (staticWarning && coreWarning) {
        break;
      }
    }

    module.provides().stream()
        .filter(p -> missingServicesMap.containsKey(p.service()))
        .forEach(p -> p.implementations().forEach(missingServicesMap.get(p.service())::remove));
  }

  public boolean staticWarning() {
    return staticWarning;
  }

  public boolean coreWarning() {
    return coreWarning;
  }

  public Map<String, Set<String>> missing() {
    return missingServicesMap;
  }
}
