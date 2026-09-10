package io.github.kazemek.jsonapi.query

import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import groovy.transform.CompileStatic
import spock.lang.Specification
import spock.lang.Unroll

class JsonApiQueryParserSpec extends Specification {

  private final JsonApiQueryParser parser = new JsonApiQueryParser()

  def "decoded multimap parses selection and retains opaque parameter order"() {
    given:
    def parameters = new LinkedHashMap<String, List<String>>()
    parameters.put("include", ["comments.author,author"])
    parameters.put("fields[articles]", ["title,title,comments"])
    parameters.put("sort", ["title,-created"])
    parameters.put("page[number]", ["2", "3"])
    parameters.put("filter[articles][status]", ["draft"])
    parameters.put("opaque", ["first", "second"])

    when:
    def query = parser.parseDecoded(parameters)

    then:
    query.selection().includeRequested()
    query.selection().includePaths() == [
      IncludePath.of("comments.author"),
      IncludePath.of("author")
    ]
    query.selection().fieldsets() == [articles: ["title", "comments"]]
    query.sortFields() == [
      new SortField("title", SortDirection.ASCENDING),
      new SortField("created", SortDirection.DESCENDING)
    ]
    query.pageParameters() == ["page[number]": ["2", "3"]]
    query.filterParameters() == ["filter[articles][status]": ["draft"]]
    query.unprocessedParameters() == [opaque: ["first", "second"]]
    query.representationSelection() == query.selection()
    query.sort() == query.sortFields()
    query.page() == query.pageParameters()
    query.filter() == query.filterParameters()
    query.unprocessed() == query.unprocessedParameters()
    SortField.descending("created").fieldName() == "created"
    SortDirection.ASC == SortDirection.ASCENDING
    SortDirection.DESC == SortDirection.DESCENDING
  }

  def "parse overloads delegate to the decoded and raw seams"() {
    expect:
    parser.parse([sort: ["title"]]).sortFields() == [SortField.ascending("title")]
    parser.parse("sort=title").sortFields() == [SortField.of("title")]
    parser.parse([sort: ["title"]], QueryAllowList.of(["x"] as Set, ["title"] as Set, [:])).sortFields() ==
    [SortField.ascending("title")]
    parser.parse("sort=title", QueryAllowList.of(["x"] as Set, ["title"] as Set, [:])).sortFields() ==
    [SortField.ascending("title")]
  }

  def "decoded seam accepts concrete list subtype values"() {
    given:
    def query = parseConcreteListSubtype(parser)

    expect:
    query.sortFields() == [SortField.ascending("title")]
  }

  def "raw and decoded seams agree for repeats, missing equals, extra equals, and delimiters"() {
    given:
    def decoded = new LinkedHashMap<String, List<String>>()
    decoded.put("include", ["comments", "author"])
    decoded.put("sort", ["-created"])
    decoded.put("page[number]", ["2", "3"])
    decoded.put("opaque", ["", "a=b"])

    when:
    parser.parseRaw("?include=comments&include=author&sort=-created&page[number]=2&page[number]=3&opaque&opaque=a=b&&")

    then:
    def rawFailure = thrown(JsonApiQueryException)
    rawFailure.diagnostic() == QueryDiagnostic.INVALID_PARAMETER_CARDINALITY
    rawFailure.parameterName() == "include"

    when:
    parser.parseDecoded(decoded)

    then:
    def decodedFailure = thrown(JsonApiQueryException)
    decodedFailure.diagnostic() == QueryDiagnostic.INVALID_PARAMETER_CARDINALITY
    decodedFailure.parameterName() == "include"

    when:
    def raw = parser.parseRaw("?page[number]=2&page[number]=3&opaque&opaque=a=b&&")
    decoded.remove("include")
    decoded.remove("sort")
    def decodedWithoutRecognized = parser.parseDecoded(decoded)

    then:
    raw == decodedWithoutRecognized
    raw.pageParameters() == ["page[number]": ["2", "3"]]
    raw.unprocessedParameters() == [opaque: ["", "a=b"]]
  }

  def "raw form decoding preserves first equals and decodes plus and encoded delimiters"() {
    when:
    def query = parser.parseRaw("filter%5Barticles%5D%5Btitle%5D=hello+world&x=a%26b%3Dc")

    then:
    query.filterParameters() == ["filter[articles][title]": ["hello world"]]
    query.unprocessedParameters() == [x: ["a&b=c"]]
  }

  def "empty raw queries contain no occurrences"() {
    expect:
    parser.parseRaw("").unprocessedParameters().isEmpty()
    parser.parseRaw("?").unprocessedParameters().isEmpty()
    parser.parseRaw("&&&").unprocessedParameters().isEmpty()
    parser.parseRaw("?&&").unprocessedParameters().isEmpty()
  }

  def "valid page and filter families remain opaque including empty brackets"() {
    when:
    def query = parser.parseDecoded([
      "page[]": ["cursor"],
      "page[number][after]": ["2"],
      "filter[articles][author.name][]": ["9"],
      "page[number": ["unprocessed"],
      "filtering": ["unprocessed"]
    ])

    then:
    query.pageParameters() == [
      "page[]": ["cursor"],
      "page[number][after]": ["2"]
    ]
    query.filterParameters() == ["filter[articles][author.name][]": ["9"]]
    query.unprocessedParameters() == [
      "page[number": ["unprocessed"],
      filtering: ["unprocessed"]
    ]
  }

  @Unroll
  def "invalid recognized syntax reports #diagnostic and #parameter"() {
    when:
    parser.parseDecoded([(parameter): value])

    then:
    def exception = thrown(JsonApiQueryException)
    exception.diagnostic() == diagnostic
    exception.parameterName() == parameter

    where:
    parameter         | value                  | diagnostic
    "include"        | ["comments,,author"]   | QueryDiagnostic.INVALID_INCLUDE_SYNTAX
    "include"        | ["comments..author"]   | QueryDiagnostic.INVALID_INCLUDE_SYNTAX
    "fields[articles]" | ["title,,body"]       | QueryDiagnostic.INVALID_FIELDSET_SYNTAX
    "sort"           | ["title,,created"]     | QueryDiagnostic.INVALID_SORT_SYNTAX
    "sort"           | ["author..name"]       | QueryDiagnostic.INVALID_SORT_SYNTAX
    "sort"           | ["-"]                  | QueryDiagnostic.INVALID_SORT_SYNTAX
    "sort"           | ["--created"]          | QueryDiagnostic.INVALID_SORT_SYNTAX
  }

  def "empty include and fieldset requests are valid under an empty allow-list"() {
    given:
    def allowList = QueryAllowList.empty()

    when:
    def query = parser.parseDecoded([
      include: [""],
      "fields[articles]": [""]
    ], allowList)

    then:
    query.selection().includeRequested()
    query.selection().includePaths().isEmpty()
    query.selection().fieldsets() == [articles: []]
  }

  def "allow-list checks exact paths fields and directionless sort fields"() {
    given:
    def allowList = QueryAllowList.of(
        ["comments.author"] as Set,
        ["created"] as Set,
        [articles: ["title"] as Set])

    expect:
    parser.parseDecoded([
      include: ["comments.author"],
      "fields[articles]": ["title"],
      sort: ["-created"]
    ], allowList).sortFields() == [
      new SortField("created", SortDirection.DESCENDING)
    ]

    when:
    parser.parseDecoded([include: ["author"]], allowList)

    then:
    def includeException = thrown(JsonApiQueryException)
    includeException.diagnostic() == QueryDiagnostic.DISALLOWED_INCLUDE_PATH
    includeException.parameterName() == "include"

    when:
    parser.parseDecoded(["fields[articles]": ["body"]], allowList)

    then:
    def fieldException = thrown(JsonApiQueryException)
    fieldException.diagnostic() == QueryDiagnostic.DISALLOWED_FIELD
    fieldException.parameterName() == "fields[articles]"

    when:
    parser.parseDecoded([sort: ["title"]], allowList)

    then:
    def sortException = thrown(JsonApiQueryException)
    sortException.diagnostic() == QueryDiagnostic.DISALLOWED_SORT_FIELD
    sortException.parameterName() == "sort"
  }

  def "dotted sort fields preserve direction and match the full allow-list token"() {
    given:
    def allowList = QueryAllowList.of(
        [] as Set,
        ["author.name", "created.at"] as Set,
        [:])

    expect:
    parser.parseDecoded([sort: ["author.name,-created.at"]], allowList).sortFields() == [
      new SortField("author.name", SortDirection.ASCENDING),
      new SortField("created.at", SortDirection.DESCENDING)
    ]
  }

  @Unroll
  def "recognized parameter #parameter requires exactly one value"() {
    when:
    parser.parseDecoded([(parameter): values])

    then:
    def exception = thrown(JsonApiQueryException)
    exception.diagnostic() == QueryDiagnostic.INVALID_PARAMETER_CARDINALITY
    exception.parameterName() == parameter

    where:
    parameter           | values
    "include"          | []
    "include"          | ["a", "b"]
    "sort"             | []
    "sort"             | ["a", "b"]
    "fields[articles]" | []
    "fields[articles]" | ["a", "b"]
  }

  @Unroll
  def "malformed fields shape #parameter is diagnosed"() {
    when:
    parser.parseDecoded([(parameter): ["value"]])

    then:
    def exception = thrown(JsonApiQueryException)
    exception.diagnostic() == QueryDiagnostic.INVALID_PARAMETER_SHAPE
    exception.parameterName() == parameter

    where:
    parameter << [
      "fields",
      "fields[articles",
      "fields[articles][comments]",
      "fields[articles]tail"
    ]
  }

  def "invalid fieldset types and empty items stay in the query diagnostic family"() {
    when:
    parser.parseDecoded(["fields[]": [""]])

    then:
    def emptyType = thrown(JsonApiQueryException)
    emptyType.diagnostic() == QueryDiagnostic.INVALID_FIELDSET_SYNTAX
    emptyType.parameterName() == "fields[]"

    when:
    parser.parseDecoded(["fields[articles]": ["title, body"]])

    then:
    def invalidName = thrown(JsonApiQueryException)
    invalidName.diagnostic() == QueryDiagnostic.INVALID_FIELDSET_SYNTAX
    invalidName.parameterName() == "fields[articles]"
  }

  @Unroll
  def "decoded malformed input #description is diagnosed without a raw null failure"() {
    when:
    parser.parseDecoded(parameters)

    then:
    def exception = thrown(JsonApiQueryException)
    exception.diagnostic() == QueryDiagnostic.INVALID_PARAMETER_SHAPE
    exception.parameterName() == expectedParameter

    where:
    description       | parameters                              | expectedParameter
    "null map"       | null                                    | null
    "null name"      | [(null): ["value"]]                    | null
    "null value list" | [include: null]                         | "include"
    "null value"     | [include: [null]]                       | "include"
    "empty name"     | [(""): ["value"]]                      | ""
  }

  def "raw malformed percent encoding attributes the decoded name when available"() {
    when:
    parser.parseRaw("%ZZ=value")

    then:
    def nameException = thrown(JsonApiQueryException)
    nameException.diagnostic() == QueryDiagnostic.MALFORMED_ENCODING
    nameException.parameterName() == null

    when:
    parser.parseRaw("include=%ZZ")

    then:
    def valueException = thrown(JsonApiQueryException)
    valueException.diagnostic() == QueryDiagnostic.MALFORMED_ENCODING
    valueException.parameterName() == "include"

    when:
    parser.parseRaw("=%ZZ")

    then:
    def emptyNameValueException = thrown(JsonApiQueryException)
    emptyNameValueException.diagnostic() == QueryDiagnostic.MALFORMED_ENCODING
    emptyNameValueException.parameterName() == ""
  }

  def "unicode whitespace-only include segments stay in the query diagnostic family"() {
    when:
    parser.parseDecoded([include: ["\u2003"]])

    then:
    def decodedException = thrown(JsonApiQueryException)
    decodedException.diagnostic() == QueryDiagnostic.INVALID_INCLUDE_SYNTAX
    decodedException.parameterName() == "include"

    when:
    parser.parseRaw("include=%E2%80%83")

    then:
    def rawException = thrown(JsonApiQueryException)
    rawException.diagnostic() == QueryDiagnostic.INVALID_INCLUDE_SYNTAX
    rawException.parameterName() == "include"
  }

  def "invalid sparse-fieldset resource types win over value cardinality"() {
    when:
    parser.parseDecoded(["fields[]": []])

    then:
    def emptyValuesException = thrown(JsonApiQueryException)
    emptyValuesException.diagnostic() == QueryDiagnostic.INVALID_FIELDSET_SYNTAX
    emptyValuesException.parameterName() == "fields[]"

    when:
    parser.parseDecoded(["fields[]": ["title", "body"]])

    then:
    def multipleValuesException = thrown(JsonApiQueryException)
    multipleValuesException.diagnostic() == QueryDiagnostic.INVALID_FIELDSET_SYNTAX
    multipleValuesException.parameterName() == "fields[]"
  }

  def "query and allow-list outputs are deeply immutable and preserve input copies"() {
    given:
    def values = new ArrayList<>(["2"])
    def parameters = new LinkedHashMap<String, List<String>>()
    parameters.put("page[number]", values)
    def includePaths = new LinkedHashSet<>(["author"])
    def sortFields = new LinkedHashSet<>(["created"])
    def fieldNames = new LinkedHashSet<>(["title"])
    def fieldsByType = new LinkedHashMap<String, Set<String>>()
    fieldsByType.put("articles", fieldNames)
    def allowList = new QueryAllowList(includePaths, sortFields, fieldsByType)

    when:
    def query = parser.parseDecoded(parameters)
    values.add("3")
    includePaths.add("comments")
    sortFields.add("title")
    fieldNames.add("body")

    then:
    query.pageParameters() == ["page[number]": ["2"]]
    allowList.includePaths() == ["author"] as Set
    allowList.sortFields() == ["created"] as Set
    allowList.fieldsByResourceType() == [articles: ["title"] as Set]
    allowList.allowedIncludePaths() == allowList.includePaths()
    allowList.allowedSortFields() == allowList.sortFields()
    allowList.fieldNamesByResourceType() == allowList.fieldsByResourceType()

    when:
    query.pageParameters().put("page[size]", ["10"])

    then:
    thrown(UnsupportedOperationException)

    when:
    query.pageParameters()["page[number]"].add("4")

    then:
    thrown(UnsupportedOperationException)

    when:
    allowList.fieldsByResourceType()["articles"].add("body")

    then:
    thrown(UnsupportedOperationException)
  }

  def "sort and selection values retain their exact non-normalized tokens"() {
    when:
    def query = parser.parseDecoded([
      include: ["comments author"],
      "fields[articles]": ["title body"],
      sort: ["title body,-created-at"]
    ])

    then:
    query.selection().includePaths() == [
      IncludePath.of("comments author")
    ]
    query.selection().fieldsets() == [articles: ["title body"]]
    query.sortFields() == [
      new SortField("title body", SortDirection.ASCENDING),
      new SortField("created-at", SortDirection.DESCENDING)
    ]
  }

  @CompileStatic
  private static JsonApiQuery parseConcreteListSubtype(JsonApiQueryParser parser) {
    Map<String, ArrayList<String>> parameters = new LinkedHashMap<>()
    def values = new ArrayList<String>()
    values.add("title")
    parameters.put("sort", values)
    return parser.parseDecoded(parameters)
  }
}
