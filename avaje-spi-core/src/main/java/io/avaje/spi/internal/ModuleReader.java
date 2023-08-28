package io.avaje.spi.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ModuleReader {

  private ModuleReader() {}

  private static final Map<String, Set<String>> foundServices = new HashMap<>();

  private static Pattern regex = Pattern.compile("provides\\s+(.*?)\\s+with");

  private static boolean staticWarning;

  public static void read(Map<String, Set<String>> missingStringsMap, BufferedReader reader)
      throws IOException {
    String line;
    String service = null;
    boolean inProvides = false;
    while ((line = reader.readLine()) != null) {

      if (line.contains("provides")) {
        inProvides = true;
        var matcher = regex.matcher(line);
        if (matcher.find()) {

          service = ProcessorUtils.shortType(matcher.group(1)).replace("$", ".");
        }
      }

      if (!inProvides || line.isBlank()) {

        if (!staticWarning
            && line.contains("requires")
            && line.contains("io.avaje.spi")
            && !line.contains("static")) {
          staticWarning = true;
        }

        continue;
      }

      processLine(line, missingStringsMap, service);

      if (line.contains(";")) {
        inProvides = false;
      }
    }
  }

  private static void processLine(
      String line, Map<String, Set<String>> missingStringsMap, String service) {
    Set<String> stringSet = missingStringsMap.computeIfAbsent(service, k -> new HashSet<>());
    Set<String> found = foundServices.computeIfAbsent(service, k -> new HashSet<>());
    if (!found.containsAll(stringSet)) {
      findMissingStrings(line, stringSet, found, service);
    }
    if (!foundServices.isEmpty()) {
      stringSet.removeAll(found);
    }
  }

  private static void findMissingStrings(
      String input, Set<String> stringSet, Set<String> foundStrings, String key) {

    for (var str : stringSet) {
      if (input.contains(str)) {
        foundStrings.add(str);
      }
    }
  }

  public static boolean staticWarning() {
    return staticWarning;
  }
}
