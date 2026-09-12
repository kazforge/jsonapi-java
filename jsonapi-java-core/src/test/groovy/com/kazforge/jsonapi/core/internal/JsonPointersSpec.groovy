package com.kazforge.jsonapi.core.internal

import spock.lang.Specification

class JsonPointersSpec extends Specification {

  def "root('#segment') is '#pointer'"() {
    expect:
    JsonPointers.root(segment) == pointer

    where:
    segment                    | pointer
    "data"                     | "/data"
    "a~b"                      | "/a~0b"
    "a/b"                      | "/a~1b"
    "https://example.com/rel"  | "/https:~1~1example.com~1rel"
    "~/"                       | "/~0~1"
  }

  def "child('#prefix', '#segment') is '#pointer'"() {
    expect:
    JsonPointers.child(prefix, segment) == pointer

    where:
    prefix   | segment                   | pointer
    ""       | "data"                    | "/data"
    null     | "data"                    | "/data"
    "/links" | "self"                    | "/links/self"
    "/links" | "https://example.com/rel" | "/links/https:~1~1example.com~1rel"
    "/a"     | "b~c/d"                   | "/a/b~0c~1d"
  }
}
