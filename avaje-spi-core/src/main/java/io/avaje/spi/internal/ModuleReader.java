package io.avaje.spi.internal;

import static java.util.stream.Collectors.toSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ModuleElement;

import io.avaje.prism.GenerateModuleInfoReader;

@GenerateModuleInfoReader
final class ModuleReader {
  private final Map<String, Set<String>> missingServicesMap = new HashMap<>();

  private boolean staticWarning;

  private boolean coreWarning;

  ModuleReader(Map<String, Set<String>> services) {
    services.forEach(this::add);
  }

  private void add(String k, Set<String> v) {
    missingServicesMap.put(Utils.fqnFromBinaryType(k), v.stream().map(Utils::fqnFromBinaryType).collect(toSet()));
  }

  void read(BufferedReader reader, ModuleElement element) throws IOException {
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

    module.provides().forEach(p -> {
      if (!missingServicesMap.containsKey(p.service())) {
        return;
      }

      var impls = p.implementations();
      var missing = missingServicesMap.get(p.service());
      if (missing.size() != impls.size()) {
        return;
      }
      impls.stream().map(Utils::fqnFromBinaryType).forEach(missing::remove);
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
