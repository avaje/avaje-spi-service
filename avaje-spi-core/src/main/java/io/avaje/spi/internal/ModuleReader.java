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

  /** Keeps Track of found services by SPI and implementation set */
  private static final Map<String, Set<String>> foundServices = new HashMap<>();

  private static Pattern regex = Pattern.compile("provides\\s+(.*?)\\s+with");

  private static boolean staticWarning;

  public static void read(Map<String, Set<String>> missingStringsMap, BufferedReader reader)
      throws IOException {
    String line;
    String service = null;
    boolean inProvides = false;
    while ((line = reader.readLine()) != null) {

      // retrieve service from provides statement
      if (line.contains("provides")) {
        inProvides = true;
        var matcher = regex.matcher(line);
        if (matcher.find()) {

          service = ProcessorUtils.shortType(matcher.group(1)).replace("$", ".");
        }
      }

      // if not part of a provides statement skip
      if (!inProvides || line.isBlank()) {

        if (!staticWarning && line.contains("io.avaje.spi") && !line.contains("static")) {
          staticWarning = true;
        }

        continue;
      }

      processLine(line, missingStringsMap, service);

      //  provides statement has ended
      if (line.contains(";")) {
        inProvides = false;
      }
    }
  }

  /** as service implementations are discovered, remove from missing strings map */
  private static void processLine(
      String line, Map<String, Set<String>> missingStringsMap, String service) {
    Set<String> stringSet = missingStringsMap.computeIfAbsent(service, k -> new HashSet<>());
    Set<String> foundStrings = foundServices.computeIfAbsent(service, k -> new HashSet<>());
    if (!foundStrings.containsAll(stringSet)) {
      findMissingStrings(line, stringSet, foundStrings);
    }
    if (!foundServices.isEmpty()) {
      stringSet.removeAll(foundStrings);
    }
  }

  /** as service implementations are discovered, add to found strings set for a given service */
  private static void findMissingStrings(
      String input, Set<String> stringSet, Set<String> foundStrings) {

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
