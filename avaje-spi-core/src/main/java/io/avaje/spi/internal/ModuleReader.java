package io.avaje.spi.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

final class ModuleReader {

  private final Map<String, Set<String>> missingServicesMap = new HashMap<>();
  private boolean staticWarning;
  private boolean coreWarning;

  ModuleReader(Map<String, Set<String>> services) {
    services.forEach(this::add);
  }

  private void add(String k, Set<String> v) {
    missingServicesMap.put(k, v.stream().collect(toSet()));
  }

  void read(ModuleInfoReader module) {
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
    module
        .provides()
        .forEach(
            p -> {
              final var contract = p.getService().getQualifiedName().toString();
              if (!missingServicesMap.containsKey(contract)) {
                return;
              }
              var impls = p.getImplementations();
              var missing = missingServicesMap.get(contract);
              impls.stream()
                  .forEach(
                      x -> {
                        missing.remove(x.getQualifiedName().toString());
                        missing.remove(APContext.elements().getBinaryName(x).toString());
                      });
            });
  }

  boolean staticWarning() {
    return staticWarning;
  }

  boolean coreWarning() {
    return coreWarning;
  }

  Map<String, Set<String>> missing() {
    return missingServicesMap;
  }
}
