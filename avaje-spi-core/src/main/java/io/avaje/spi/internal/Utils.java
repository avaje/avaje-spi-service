package io.avaje.spi.internal;

import static io.avaje.spi.internal.APContext.typeElement;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class Utils {

  static String fqnFromBinaryType(String binaryType) {
    var type = typeElement(binaryType.replace('$', '.'));
    if (type != null) {
      return type.getQualifiedName().toString();
    }

    type = typeElement(replaceDollar(binaryType));
    if (type != null) {
      return type.getQualifiedName().toString();
    }

    return binaryType;
  }

  // replace '$' with '.' only if there is a lowercase letter in front and uppercase letter behind
  static String replaceDollar(String str) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length(); i++) {
      char currChar = str.charAt(i);
      if (currChar == '$' && i > 0 && i < str.length() - 1) {
        char prevChar = str.charAt(i - 1);
        char nextChar = str.charAt(i + 1);
        if (Character.isLowerCase(prevChar) && Character.isUpperCase(nextChar)) {
          sb.append('.');
        } else {
          sb.append(currChar);
        }
      } else {
        sb.append(currChar);
      }
    }
    return sb.toString();
  }

  static void mergeServices(Map<String, Set<String>> newMap, Map<String, Set<String>> oldMap) {
    newMap.forEach((key, value) ->
      oldMap.merge(key, value, (oldValue, newValue) -> {
        oldValue.addAll(newValue);
        return oldValue;
      }));
  }
}
