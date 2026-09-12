package com.kazforge.jsonapi.core.internal

import spock.lang.Specification

class SyntaxValidatorsSpec extends Specification {

  def "isValidUriReference('#value') is #valid"() {
    expect:
    SyntaxValidators.isValidUriReference(value) == valid

    where:
    value                                         | valid
    "http://example.com/path"                     | true
    "http://example.com/path?q=1#frag"            | true
    "http://example.com/%20space"                 | true
    "/relative/path"                              | true
    "//example.com/network-path"                  | true
    "?query-only"                                 | true
    "#fragment-only"                              | true
    "relative-segment"                            | true
    "mailto:user@example.com"                     | true
    "http:/absolute-path"                         | true
    "http:"                                       | true
    ""                                            | true
    "foo%20bar"                                   | true
    "//example.com/%2Fpath"                       | true
    "http://example.com/path?q=%20#f=%2F"         | true
    ("http://user:pass@[::1]:8080/path")          | true
    "http://example.com:8080/"                    | true
    ("http://[v1.something]/")                    | true
    "http://192.168.0.1/"                         | true
    ("http://[bad]/")                             | false
    ("http://[::1/")                              | false
    "http://example.com:abc/"                     | false
    "//user@@host/"                               | false
    ("http://[]/")                                | false
    ("http://[::1][::2]/")                        | false
    ":"                                           | false
    "1abc:def"                                    | false
    "//example.com/%zz"                           | false
    "http://example.com/path?q=%zz"               | false
    "http://example.com/path#f=%zz"               | false
    "http://exam ple.com/"                        | false
    "http://example.com/with spaces"              | false
    "http://example.com/caf\u00e9"                | false
    "http://example.com/%zz"                      | false
    "http://example.com/\u0001"                   | false
    null                                          | false
  }

  def "isValidLinkRelation('#value') is #valid"() {
    expect:
    SyntaxValidators.isValidLinkRelation(value) == valid

    where:
    value                    | valid
    "self"                   | true
    "describedby"            | true
    "http://example.com/rel" | true
    "SELF"                   | false
    "_bad"                   | false
    "author name"            | false
    "has_underscore"         | false
    null                     | false
    ""                       | false
    "/relative"              | false
    ("http://[::1/")         | false
  }

  def "isValidLanguageTag('#value') is #valid"() {
    expect:
    SyntaxValidators.isValidLanguageTag(value) == valid

    where:
    value          | valid
    "en"           | true
    "en-US"        | true
    "x-private"    | true
    "a"            | false
    "invalid tag!" | false
    "en-u-"        | false
    null           | false
    ""             | false
  }

  def "isValidMediaType('#value') is #valid"() {
    expect:
    SyntaxValidators.isValidMediaType(value) == valid

    where:
    value                                                             | valid
    "application/vnd.api+json"                                        | true
    "text/html"                                                       | true
    "application/foo~bar"                                             | true
    'application/vnd.api+json; profile="https://example.com/profile"' | true
    'application/json; foo="a\\"b"'                                   | true
    'application/json; note="a;b"'                                    | true
    "application/vnd.api+json;charset=utf-8"                          | true
    'application/json; note="a\tb"'                                   | true
    'application/json; charset=utf-8; foo="bar"'                      | true
    'application/json; foo="a\\\u0080b"'                              | true
    "application/json; \tcharset=utf-8"                               | true
    "application/json ; charset=utf-8"                                | true
    "application/json\t; charset=utf-8"                               | true
    "not-a-media-type"                                                | false
    "application/vnd.api+json;"                                       | false
    "application/json;;charset=utf-8"                                 | false
    "application / json"                                              | false
    "application/json; charset = utf-8"                               | false
    'application/json; foo="a"b"'                                     | false
    'application/json; foo="unterminated'                             | false
    'application/json; foo="a\nb"'                                    | false
    'application/json; foo="a\\\nb"'                                  | false
    "application/json;\u2003charset=utf-8"                            | false
    'application/json; foo="a\u0100b"'                                | false
    'application/json; foo="a\\\u0100b"'                              | false
    null                                                              | false
    ""                                                                | false
    "application/"                                                    | false
    "/json"                                                           | false
    " application/json"                                               | false
    "\tapplication/json"                                              | false
    "application/json "                                               | false
    "application/json\t"                                              | false
    "application/json; charset=utf-8 "                                | false
    "application/json; charset=utf-8\t"                               | false
  }

  def "isValidExtensionOrProfileUri('#value') is #valid"() {
    expect:
    SyntaxValidators.isValidExtensionOrProfileUri(value) == valid

    where:
    value                           | valid
    "https://example.com/ext"       | true
    "urn:example:ext"               | true
    ("http://[bad]")                | false
    "http://example.com:abc/"       | false
    "/relative/ext"                 | false
    "not a uri"                     | false
    "https://example.com/caf\u00e9" | false
    null                            | false
    ""                              | false
  }

  def "isValidJsonPointer('#value') is #valid"() {
    expect:
    SyntaxValidators.isValidJsonPointer(value) == valid

    where:
    value        | valid
    ""           | true
    "/"          | true
    "/data"      | true
    "/data/0/id" | true
    "/a~0b"      | true
    "/a~1b"      | true
    "/a~01b"     | true
    "/données"   | true
    "data"       | false
    "/a~"        | false
    "/a~2"       | false
    "/a~x"       | false
    "#/data"     | false
    null         | false
  }
}
