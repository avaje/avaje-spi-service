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

  public static void read(Map<String, Set<String>> missingServicesMap, BufferedReader reader)
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

      processLine(line, missingServicesMap, service);

      //  provides statement has ended
      if (line.contains(";")) {
        inProvides = false;
      }
    }
  }

  /** as service implementations are discovered, remove from missing strings map */
  private static void processLine(
      String line, Map<String, Set<String>> missingServicesMap, String service) {
    Set<String> missingServiceImpls = missingServicesMap.get(service);
    Set<String> foundServiceImpls = foundServices.computeIfAbsent(service, k -> new HashSet<>());
    if (!foundServiceImpls.containsAll(missingServiceImpls)) {
      addFoundStrings(line, missingServiceImpls, foundServiceImpls);
    }
    missingServiceImpls.removeAll(foundServiceImpls);
  }

  /**
   * as service implementations are discovered, add to found strings set for a given service
   *
   * @param input the line to check
   * @param missingServiceImpls the services we're looking for
   * @param foundServiceImpls where we'll store the results if we have a match
   */
  private static void addFoundStrings(
      String input, Set<String> missingServiceImpls, Set<String> foundServiceImpls) {

    for (var impl : missingServiceImpls) {
      if (input.contains(impl)) {
        foundServiceImpls.add(impl);
      }
    }
  }

  public static boolean staticWarning() {
    return staticWarning;
  }
}
