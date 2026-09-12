package com.kazforge.jsonapi.core.internal;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Syntax validation for URI references, link relations, language tags, media types, and JSON
 * Pointers.
 */
public final class SyntaxValidators {

  /** RFC 7230 tchar: token characters for media-type type/subtype and parameter names/values. */
  private static final Pattern MEDIA_TYPE_TOKEN = Pattern.compile("^[a-zA-Z0-9!#$%&'*+.^_`|~-]++$");

  /** RFC 8288 / 5988 registered relation: LOALPHA *( LOALPHA / DIGIT / "." / "-" ). */
  private static final Pattern REGISTERED_RELATION = Pattern.compile("^[a-z][a-z0-9.-]*+$");

  private SyntaxValidators() {}

  /**
   * RFC 3986 URI-reference (ASCII). Empty string is valid; {@code null} is not. Raw non-ASCII is
   * rejected (percent-encoding required).
   */
  public static boolean isValidUriReference(@Nullable String value) {
    if (value == null) {
      return false;
    }
    if (value.isEmpty()) {
      return true;
    }
    return parseUriReference(value, false);
  }

  public static boolean isValidLinkRelation(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    if (REGISTERED_RELATION.matcher(value).matches()) {
      return true;
    }
    return parseUriReference(value, true);
  }

  public static boolean isValidLanguageTag(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    try {
      new Locale.Builder().setLanguageTag(value).build();
      return true;
    } catch (IllformedLocaleException ex) {
      return false;
    }
  }

  public static boolean isValidMediaType(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    int semicolon = indexOfUnquotedSemicolon(value, 0);
    // RFC 7231: type "/" subtype *( OWS ";" OWS parameter ) — OWS only immediately before ';'.
    // Leading OWS and terminal OWS with no parameter are illegal.
    String typeSubtype = semicolon < 0 ? value : rstripHttpOws(value.substring(0, semicolon));
    if (!isValidMediaTypeTypeSubtype(typeSubtype)) {
      return false;
    }
    return semicolon < 0 || areValidMediaTypeParameters(value, semicolon + 1);
  }

  private static boolean isValidMediaTypeTypeSubtype(String typeSubtype) {
    int slash = typeSubtype.indexOf('/');
    if (slash <= 0 || slash == typeSubtype.length() - 1) {
      return false;
    }
    String type = typeSubtype.substring(0, slash);
    String subtype = typeSubtype.substring(slash + 1);
    return MEDIA_TYPE_TOKEN.matcher(type).matches() && MEDIA_TYPE_TOKEN.matcher(subtype).matches();
  }

  private static boolean areValidMediaTypeParameters(String value, int start) {
    int pos = start;
    while (true) {
      int next = indexOfUnquotedSemicolon(value, pos);
      // Intermediate segments: OWS on both sides of ';'. Final parameter: leading OWS only.
      String parameter =
          next < 0 ? lstripHttpOws(value.substring(pos)) : stripHttpOws(value.substring(pos, next));
      if (!isValidMediaTypeParameter(parameter)) {
        return false;
      }
      if (next < 0) {
        return true;
      }
      pos = next + 1;
    }
  }

  public static boolean isValidExtensionOrProfileUri(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    return parseUriReference(value, true);
  }

  /**
   * RFC 6901 JSON Pointer syntax only. Empty string is valid; {@code null} is not. Does not resolve
   * against a document. URI-fragment form ({@code #/…}) is rejected.
   */
  public static boolean isValidJsonPointer(@Nullable String value) {
    if (value == null) {
      return false;
    }
    if (value.isEmpty()) {
      return true;
    }
    if (value.charAt(0) != '/') {
      return false;
    }
    int i = 0;
    while (i < value.length()) {
      if (value.charAt(i) != '~') {
        i++;
        continue;
      }
      if (i + 1 >= value.length()) {
        return false;
      }
      char escape = value.charAt(i + 1);
      if (escape != '0' && escape != '1') {
        return false;
      }
      i += 2;
    }
    return true;
  }

  /**
   * Parses an RFC 3986 URI-reference. When {@code absoluteOnly} is true, requires {@code scheme ":"
   * hier-part}.
   */
  private static boolean parseUriReference(String value, boolean absoluteOnly) {
    if (!isAscii(value)) {
      return false;
    }
    int schemeEnd = schemeEnd(value);
    if (absoluteOnly) {
      if (schemeEnd < 0) {
        return false;
      }
      return parseHierQueryFragment(value, schemeEnd + 1);
    }
    if (schemeEnd >= 0) {
      return parseHierQueryFragment(value, schemeEnd + 1);
    }
    return parseRelativeRef(value);
  }

  private static boolean isAscii(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) > 0x7F) {
        return false;
      }
    }
    return true;
  }

  /** Returns index of ':' after a valid scheme, or -1. */
  private static int schemeEnd(String value) {
    if (value.isEmpty() || !isAlpha(value.charAt(0))) {
      return -1;
    }
    for (int i = 1; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == ':') {
        return i;
      }
      if (!(isAlpha(ch) || isDigit(ch) || ch == '+' || ch == '-' || ch == '.')) {
        return -1;
      }
    }
    return -1;
  }

  private static boolean parseRelativeRef(String value) {
    if (value.startsWith("//")) {
      return parseAuthorityPathQueryFragment(value, 2);
    }
    if (value.startsWith("/")) {
      return parseAbsPathQueryFragment(value, 0);
    }
    if (value.startsWith("?") || value.startsWith("#")) {
      return parseQueryFragment(value, 0);
    }
    int firstSegmentEnd = scanPathNoschemeSegment(value);
    if (firstSegmentEnd <= 0) {
      return false;
    }
    return parsePathRestQueryFragment(value, firstSegmentEnd);
  }

  /** Scans path-noscheme first segment (segment-nz-nc). Returns end index, or -1 if invalid. */
  private static int scanPathNoschemeSegment(String value) {
    int i = 0;
    while (i < value.length()) {
      char ch = value.charAt(i);
      if (ch == '/' || ch == '?' || ch == '#') {
        break;
      }
      if (ch == ':') {
        return -1;
      }
      if (!isPchar(ch) && isNotPctEncoded(value, i)) {
        return -1;
      }
      i += advancePcharOrPct(ch);
    }
    return i == 0 ? -1 : i;
  }

  private static boolean parseHierQueryFragment(String value, int from) {
    if (from > value.length()) {
      return false;
    }
    if (from + 1 < value.length() && value.startsWith("//", from)) {
      return parseAuthorityPathQueryFragment(value, from + 2);
    }
    if (from < value.length() && value.charAt(from) == '/') {
      return parseAbsPathQueryFragment(value, from);
    }
    if (from == value.length() || value.charAt(from) == '?' || value.charAt(from) == '#') {
      // path-empty
      return parseQueryFragment(value, from);
    }
    // path-rootless
    return parsePathRootlessQueryFragment(value, from);
  }

  private static boolean parseAuthorityPathQueryFragment(String value, int from) {
    int end = from;
    while (end < value.length()) {
      char ch = value.charAt(end);
      if (ch == '/' || ch == '?' || ch == '#') {
        break;
      }
      end++;
    }
    if (!isValidAuthority(value.substring(from, end))) {
      return false;
    }
    return parsePathRestQueryFragment(value, end);
  }

  /**
   * RFC 3986 authority = [ userinfo "@" ] host [ ":" port ]. Uses the last {@code @} as the
   * userinfo separator.
   */
  private static boolean isValidAuthority(String authority) {
    int at = authority.lastIndexOf('@');
    String hostPort;
    if (at >= 0) {
      if (!isValidUserinfo(authority.substring(0, at))) {
        return false;
      }
      hostPort = authority.substring(at + 1);
    } else {
      hostPort = authority;
    }
    return isValidHostPort(hostPort);
  }

  private static boolean isValidUserinfo(String userinfo) {
    int i = 0;
    while (i < userinfo.length()) {
      char ch = userinfo.charAt(i);
      if (ch == '%') {
        if (isNotPctEncoded(userinfo, i)) {
          return false;
        }
        i += 3;
        continue;
      }
      if (!(isUnreserved(ch) || isSubDelim(ch) || ch == ':')) {
        return false;
      }
      i++;
    }
    return true;
  }

  private static boolean isValidHostPort(String hostPort) {
    if (hostPort.startsWith("[")) {
      int close = hostPort.indexOf(']');
      if (close < 0) {
        return false;
      }
      if (!isValidIpLiteral(hostPort.substring(1, close))) {
        return false;
      }
      return isValidOptionalPort(hostPort.substring(close + 1));
    }
    int colon = hostPort.indexOf(':');
    String host = colon < 0 ? hostPort : hostPort.substring(0, colon);
    if (!isValidRegName(host)) {
      return false;
    }
    return colon < 0 || isValidOptionalPort(hostPort.substring(colon));
  }

  private static boolean isValidOptionalPort(String portPart) {
    if (portPart.isEmpty()) {
      return true;
    }
    if (portPart.charAt(0) != ':') {
      return false;
    }
    for (int i = 1; i < portPart.length(); i++) {
      if (!isDigit(portPart.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidRegName(String host) {
    int i = 0;
    while (i < host.length()) {
      char ch = host.charAt(i);
      if (ch == '%') {
        if (isNotPctEncoded(host, i)) {
          return false;
        }
        i += 3;
        continue;
      }
      if (!(isUnreserved(ch) || isSubDelim(ch))) {
        return false;
      }
      i++;
    }
    return true;
  }

  /** IP-literal contents: IPv6address / IPvFuture (brackets already stripped). */
  private static boolean isValidIpLiteral(String literal) {
    if (literal.isEmpty()) {
      return false;
    }
    if (literal.charAt(0) == 'v' || literal.charAt(0) == 'V') {
      return isValidIpvFuture(literal);
    }
    return isValidIpv6Address(literal);
  }

  /** IPvFuture = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" ) */
  private static boolean isValidIpvFuture(String literal) {
    if (literal.length() < 4 || (literal.charAt(0) != 'v' && literal.charAt(0) != 'V')) {
      return false;
    }
    int i = 1;
    boolean sawHex = false;
    while (i < literal.length() && isHexDigit(literal.charAt(i))) {
      sawHex = true;
      i++;
    }
    if (!sawHex || i >= literal.length() || literal.charAt(i) != '.') {
      return false;
    }
    i++;
    if (i >= literal.length()) {
      return false;
    }
    while (i < literal.length()) {
      char ch = literal.charAt(i);
      if (!(isUnreserved(ch) || isSubDelim(ch) || ch == ':')) {
        return false;
      }
      i++;
    }
    return true;
  }

  /**
   * Validates IPv6address per RFC 3986 (including IPv4-mapped ls32). Rejects non-hex content such
   * as {@code bad}.
   */
  private static boolean isValidIpv6Address(String address) {
    int doubleColon = address.indexOf("::");
    if (doubleColon >= 0) {
      if (address.indexOf("::", doubleColon + 1) >= 0) {
        return false;
      }
      String left = address.substring(0, doubleColon);
      String right = address.substring(doubleColon + 2);
      int leftCount = countIpv6Pieces(left, false);
      int rightCount = countIpv6Pieces(right, true);
      if (leftCount < 0 || rightCount < 0) {
        return false;
      }
      // "::" compresses at least one 16-bit piece; total pieces must be < 8.
      return leftCount + rightCount < 8;
    }
    return countIpv6Pieces(address, true) == 8;
  }

  /**
   * Counts h16 / ls32 pieces in a non-compressed IPv6 side. When {@code allowIpv4Tail} is true, the
   * final piece may be an IPv4address counted as two pieces.
   *
   * @return piece count, or -1 if invalid
   */
  private static int countIpv6Pieces(String side, boolean allowIpv4Tail) {
    if (side.isEmpty()) {
      return 0;
    }
    String[] parts = side.split(":", -1);
    if (parts.length == 0) {
      return -1;
    }
    int pieces = 0;
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (part.isEmpty()) {
        return -1;
      }
      boolean last = i == parts.length - 1;
      if (last && allowIpv4Tail && part.indexOf('.') >= 0) {
        if (!isValidIpv4Address(part)) {
          return -1;
        }
        pieces += 2;
        continue;
      }
      if (!isH16(part)) {
        return -1;
      }
      pieces++;
    }
    return pieces;
  }

  private static boolean isH16(String value) {
    if (value.isEmpty() || value.length() > 4) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!isHexDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidIpv4Address(String value) {
    String[] parts = value.split("\\.", -1);
    if (parts.length != 4) {
      return false;
    }
    for (String part : parts) {
      if (!isDecOctet(part)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isDecOctet(String value) {
    if (value.isEmpty() || value.length() > 3) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!isDigit(value.charAt(i))) {
        return false;
      }
    }
    if (value.length() > 1 && value.charAt(0) == '0') {
      return false;
    }
    return Integer.parseInt(value) <= 255;
  }

  private static boolean parseAbsPathQueryFragment(String value, int from) {
    return parsePathRestQueryFragment(value, from);
  }

  private static boolean parsePathRootlessQueryFragment(String value, int from) {
    int i = from;
    boolean sawSegment = false;
    while (i < value.length()) {
      char ch = value.charAt(i);
      if (ch == '?' || ch == '#') {
        break;
      }
      if (ch == '/') {
        if (!sawSegment) {
          return false;
        }
        i++;
      } else if (!isPchar(ch) && isNotPctEncoded(value, i)) {
        return false;
      } else {
        sawSegment = true;
        i += advancePcharOrPct(ch);
      }
    }
    if (!sawSegment) {
      return false;
    }
    return parseQueryFragment(value, i);
  }

  private static boolean parsePathRestQueryFragment(String value, int from) {
    int i = from;
    while (i < value.length()) {
      char ch = value.charAt(i);
      if (ch == '?' || ch == '#') {
        break;
      }
      if (ch == '/') {
        i++;
      } else if (!isPchar(ch) && isNotPctEncoded(value, i)) {
        return false;
      } else {
        i += advancePcharOrPct(ch);
      }
    }
    return parseQueryFragment(value, i);
  }

  private static int advancePcharOrPct(char ch) {
    return ch == '%' ? 3 : 1;
  }

  private static boolean parseQueryFragment(String value, int from) {
    int i = scanQuery(value, from);
    if (i < 0) {
      return false;
    }
    i = scanFragment(value, i);
    return i == value.length();
  }

  /** Scans optional {@code ?query}. Returns new index, or -1 on invalid query chars. */
  private static int scanQuery(String value, int from) {
    if (from >= value.length() || value.charAt(from) != '?') {
      return from;
    }
    return scanQueryOrFragmentBody(value, from + 1, '#');
  }

  /** Scans optional {@code #fragment}. Returns new index, or -1 on invalid fragment chars. */
  private static int scanFragment(String value, int from) {
    if (from >= value.length() || value.charAt(from) != '#') {
      return from;
    }
    return scanQueryOrFragmentBody(value, from + 1, '\0');
  }

  private static int scanQueryOrFragmentBody(String value, int from, char terminator) {
    int i = from;
    while (i < value.length()) {
      char ch = value.charAt(i);
      if (terminator != '\0' && ch == terminator) {
        return i;
      }
      if (!isQueryOrFragmentChar(ch) && isNotPctEncoded(value, i)) {
        return -1;
      }
      i += advancePcharOrPct(ch);
    }
    return i;
  }

  private static boolean isQueryOrFragmentChar(char ch) {
    return isPchar(ch) || ch == '/' || ch == '?';
  }

  private static boolean isNotPctEncoded(String value, int index) {
    if (index + 2 >= value.length() || value.charAt(index) != '%') {
      return true;
    }
    return !(isHexDigit(value.charAt(index + 1)) && isHexDigit(value.charAt(index + 2)));
  }

  private static boolean isPchar(char ch) {
    return isUnreserved(ch) || isSubDelim(ch) || ch == ':' || ch == '@';
  }

  private static boolean isUnreserved(char ch) {
    return isAlpha(ch) || isDigit(ch) || ch == '-' || ch == '.' || ch == '_' || ch == '~';
  }

  private static boolean isSubDelim(char ch) {
    return ch == '!'
        || ch == '$'
        || ch == '&'
        || ch == '\''
        || ch == '('
        || ch == ')'
        || ch == '*'
        || ch == '+'
        || ch == ','
        || ch == ';'
        || ch == '=';
  }

  private static boolean isAlpha(char ch) {
    return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
  }

  private static boolean isDigit(char ch) {
    return ch >= '0' && ch <= '9';
  }

  private static boolean isHexDigit(char ch) {
    return isDigit(ch) || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f');
  }

  private static int indexOfUnquotedSemicolon(String value, int from) {
    boolean inQuotes = false;
    boolean escaped = false;
    for (int i = from; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (inQuotes) {
        if (ch == '\\') {
          escaped = true;
        } else if (ch == '"') {
          inQuotes = false;
        }
      } else if (ch == '"') {
        inQuotes = true;
      } else if (ch == ';') {
        return i;
      }
    }
    return -1;
  }

  private static boolean isValidMediaTypeParameter(String parameter) {
    if (parameter.isEmpty()) {
      return false;
    }
    int eq = parameter.indexOf('=');
    if (eq <= 0) {
      return false;
    }
    String name = parameter.substring(0, eq);
    String rawValue = parameter.substring(eq + 1);
    if (!MEDIA_TYPE_TOKEN.matcher(name).matches() || rawValue.isEmpty()) {
      return false;
    }
    if (rawValue.charAt(0) == '"') {
      return isValidQuotedString(rawValue);
    }
    return MEDIA_TYPE_TOKEN.matcher(rawValue).matches();
  }

  /**
   * RFC 7230 quoted-string: qdtext includes HTAB; quoted-pair allows HTAB / SP / VCHAR / obs-text
   * only (not bare or escaped CR/LF).
   */
  private static boolean isValidQuotedString(String value) {
    if (value.length() < 2 || value.charAt(0) != '"') {
      return false;
    }
    boolean escaped = false;
    for (int i = 1; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (escaped) {
        if (!isQuotedPairChar(ch)) {
          return false;
        }
        escaped = false;
      } else if (ch == '\\') {
        escaped = true;
      } else if (ch == '"') {
        return i == value.length() - 1;
      } else if (!isQdtext(ch)) {
        return false;
      }
    }
    return false;
  }

  private static boolean isQdtext(char ch) {
    if (ch == 0x09 || ch == 0x20 || ch == 0x21) {
      return true;
    }
    if (ch >= 0x23 && ch <= 0x5B) {
      return true;
    }
    if (ch >= 0x5D && ch <= 0x7E) {
      return true;
    }
    return isObsText(ch);
  }

  private static boolean isQuotedPairChar(char ch) {
    if (ch == 0x09 || ch == 0x20) {
      return true;
    }
    if (ch >= 0x21 && ch <= 0x7E) {
      return true;
    }
    return isObsText(ch);
  }

  /** RFC 7230 obs-text = %x80-FF (octet range; not arbitrary Unicode). */
  private static boolean isObsText(char ch) {
    return ch >= 0x80 && ch <= 0xFF;
  }

  /** RFC 7230 OWS = *( SP / HTAB ). */
  private static String stripHttpOws(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
      char ch = value.charAt(start);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      start++;
    }
    while (end > start) {
      char ch = value.charAt(end - 1);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      end--;
    }
    return value.substring(start, end);
  }

  /** Trailing RFC 7230 OWS only (SP / HTAB). */
  private static String rstripHttpOws(String value) {
    int end = value.length();
    while (end > 0) {
      char ch = value.charAt(end - 1);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      end--;
    }
    return value.substring(0, end);
  }

  /** Leading RFC 7230 OWS only (SP / HTAB). */
  private static String lstripHttpOws(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
      char ch = value.charAt(start);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      start++;
    }
    return value.substring(start, end);
  }
}
