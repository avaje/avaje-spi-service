package io.avaje.spi.internal;

import static io.avaje.spi.internal.APContext.typeElement;

public class Utils {

  static String getFQNFromBinaryType(String binaryType) {

    var type = typeElement(binaryType.replace("$", "."));

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
  public static String replaceDollar(String str) {
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
}
