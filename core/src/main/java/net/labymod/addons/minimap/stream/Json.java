package net.labymod.addons.minimap.stream;

/**
 * Tiny dependency-free JSON helper. Outgoing messages are assembled with {@link #escape(String)};
 * the few small, flat control messages coming from the phone are read with the {@code get*}
 * extractors. This is intentionally minimal &mdash; both ends of this protocol are ours.
 */
public final class Json {

  private Json() {
  }

  public static String escape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (c < 0x20) {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    return builder.toString();
  }

  /** Extracts a string value for {@code key} from a flat JSON object, or {@code null}. */
  public static String getString(String json, String key) {
    int valueStart = valueStart(json, key);
    if (valueStart < 0 || json.charAt(valueStart) != '"') {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (int i = valueStart + 1; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '\\' && i + 1 < json.length()) {
        char next = json.charAt(++i);
        builder.append(switch (next) {
          case 'n' -> '\n';
          case 'r' -> '\r';
          case 't' -> '\t';
          default -> next;
        });
      } else if (c == '"') {
        return builder.toString();
      } else {
        builder.append(c);
      }
    }
    return null;
  }

  public static Integer getInt(String json, String key) {
    String token = rawToken(json, key);
    if (token == null) {
      return null;
    }
    try {
      return (int) Double.parseDouble(token);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  public static boolean getBool(String json, String key, boolean fallback) {
    String token = rawToken(json, key);
    if (token == null) {
      return fallback;
    }
    return token.equals("true");
  }

  private static String rawToken(String json, String key) {
    int valueStart = valueStart(json, key);
    if (valueStart < 0) {
      return null;
    }
    int end = valueStart;
    while (end < json.length()) {
      char c = json.charAt(end);
      if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
        break;
      }
      end++;
    }
    return end > valueStart ? json.substring(valueStart, end) : null;
  }

  private static int valueStart(String json, String key) {
    if (json == null) {
      return -1;
    }
    int keyIndex = json.indexOf("\"" + key + "\"");
    if (keyIndex < 0) {
      return -1;
    }
    int colon = json.indexOf(':', keyIndex + key.length() + 2);
    if (colon < 0) {
      return -1;
    }
    int i = colon + 1;
    while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
      i++;
    }
    return i < json.length() ? i : -1;
  }
}
