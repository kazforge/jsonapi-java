package com.kazforge.jsonapi.core.model;

import java.util.Set;

/**
 * Shared JSON:API v1.1 wire member vocabulary.
 *
 * <p>Codecs and reserved-name sets use these constants so document keys stay aligned across
 * modules. Not an application-facing construction API.
 */
public final class JsonApiMembers {

  private JsonApiMembers() {}

  // Document
  public static final String DATA = "data";
  public static final String ERRORS = "errors";
  public static final String META = "meta";
  public static final String JSONAPI = "jsonapi";
  public static final String LINKS = "links";
  public static final String INCLUDED = "included";

  // Resource / identifier
  public static final String TYPE = "type";
  public static final String ID = "id";
  public static final String LID = "lid";
  public static final String ATTRIBUTES = "attributes";
  public static final String RELATIONSHIPS = "relationships";

  // Link object
  public static final String HREF = "href";
  public static final String REL = "rel";
  public static final String DESCRIBEDBY = "describedby";
  public static final String TITLE = "title";
  public static final String HREFLANG = "hreflang";

  // Link names
  public static final String SELF = "self";
  public static final String RELATED = "related";
  public static final String FIRST = "first";
  public static final String LAST = "last";
  public static final String PREV = "prev";
  public static final String NEXT = "next";
  public static final String ABOUT = "about";

  // jsonapi object
  public static final String VERSION = "version";
  public static final String EXT = "ext";
  public static final String PROFILE = "profile";

  // Error
  public static final String STATUS = "status";
  public static final String CODE = "code";
  public static final String DETAIL = "detail";
  public static final String SOURCE = "source";
  public static final String POINTER = "pointer";
  public static final String PARAMETER = "parameter";
  public static final String HEADER = "header";

  /** Standard pagination link names ({@code first}, {@code last}, {@code prev}, {@code next}). */
  public static final Set<String> PAGINATION_LINKS = Set.of(FIRST, LAST, PREV, NEXT);
}
