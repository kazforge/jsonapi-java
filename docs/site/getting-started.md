---
title: "Install JSON:API for Java with Jackson 3"
description: "Install jsonapi-java from Maven Central for Java 21+ and Jackson 3, and read and write a resource DTO."
---

# Install JSON:API for Java with Jackson 3 {#getting-started}

## Choose an artifact

Five consumer artifacts are published together on one release train:

| Artifact (`com.kazforge`) | When to use it |
|---------------------------|----------------|
| `jsonapi-java-jackson3` | Native runtime for a configured `tools.jackson.databind.json.JsonMapper` |
| `jsonapi-java-query` | Optional parsing of `include`, `fields`, `sort`, and opaque parameters |
| `jsonapi-java-core` | Immutable documents and validation without a JSON backend |
| `jsonapi-java-api` | Neutral `JsonApi`, mapping, representation, and PATCH contracts; no runtime by itself |
| `jsonapi-java-annotations` | Mapping role annotations without a runtime dependency |

`jsonapi-java-jackson3` brings API, annotations, and core transitively. The public
`com.kazforge.jsonapi.mapping` package ships in `jsonapi-java-api`. Packages containing `internal`
are unsupported implementation details, and Java visibility alone is not a compatibility promise.

## Compatibility

| Requirement | Minimum supported |
|-------------|-------------------|
| Java | 21 |
| Jackson 3 (`jsonapi-java-jackson3`) | `jackson-databind` 3.1.7 |

<!-- x-release-please-start-version -->
Compatible newer Jackson 3 versions can be selected by your dependency management; the adapter does
not force a platform or override your application's Jackson version. The LTS baseline and policy are
maintained in the
[Jackson 3 support section](https://github.com/kazforge/jsonapi-java/blob/v0.4.0/jsonapi-java-jackson3/README.md#jackson-support).
The supported Jackson line is not a Spring Boot compatibility matrix.
<!-- x-release-please-end -->

Starting in `0.3.0`, `jsonapi-java` supports Jackson 3 (`tools.jackson.*`) only;
`jsonapi-java-jackson2` and the separately published internal `jsonapi-java-mapping` artifact from
`0.2.0` are discontinued. Historical `0.2.0` artifacts remain on Maven Central without ongoing
support.

## Dependency declarations

Use the same version for every `jsonapi-java-*` artifact; there is no BOM.
Verify public versions in [Maven Central](https://central.sonatype.com/namespace/com.kazforge).

<!-- x-release-please-start-version -->

=== "Gradle Kotlin DSL"

    ```kotlin
    repositories {
        mavenCentral()
    }

    val jsonapiVersion = "0.4.0"

    dependencies {
        implementation("com.kazforge:jsonapi-java-jackson3:$jsonapiVersion")
        // Optional: query parsing
        // implementation("com.kazforge:jsonapi-java-query:$jsonapiVersion")
    }
    ```

=== "Maven"

    ```xml
    <properties>
        <jsonapi.version>0.4.0</jsonapi.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>com.kazforge</groupId>
            <artifactId>jsonapi-java-jackson3</artifactId>
            <version>${jsonapi.version}</version>
        </dependency>
        <!-- Optional: query parsing
        <dependency>
            <groupId>com.kazforge</groupId>
            <artifactId>jsonapi-java-query</artifactId>
            <version>${jsonapi.version}</version>
        </dependency>
        -->
    </dependencies>
    ```

<!-- x-release-please-end -->

Add `jsonapi-java-query` separately if you need [query parsing](query-and-representation.md).

Before 1.0, a breaking change ships in a minor release, never a patch release. Review
[release notes](https://github.com/kazforge/jsonapi-java/releases)
when upgrading across minors. Raising a supported Java or Jackson minimum is a breaking change too.

## Configure a runtime

Configure Jackson first, then pass the mapper to `JsonApiJackson3`. Keep the resulting immutable,
thread-safe runtime for your application's lifetime. Factories do not mutate your mapper.

```java
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import tools.jackson.databind.json.JsonMapper;

JsonMapper mapper = JsonMapper.builder()
    .build();

JsonApi api = JsonApiJackson3.jsonApi(mapper);
```

For non-default application-lifetime settings, use the [configuration guide](configuration.md).
It covers the builder's identifier conversion, linkage mappers, representation policy,
decorators, and optional resource-write `jsonapi.version`. Selection and document envelopes belong
to each operation.

## Read and write an article

This complete application writes an article, reads it back, and prints its title:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import tools.jackson.databind.json.JsonMapper;

public class Example {
    @JsonApiResource(type = "articles")
    public record Article(
        @JsonApiId String id,
        @JsonApiAttribute String title
    ) {}

    public static void main(String[] args) {
        JsonMapper mapper = JsonMapper.builder()
            .build();
        JsonApi api = JsonApiJackson3.jsonApi(mapper);

        Article article = new Article(
            "1",
            "Working with JSON:API"
        );
        String json = api.resources().writeOne(article);

        Article readBack = api.resources().readOne(json, Article.class);
        System.out.println(readBack.title());
    }
}
```

Console output:

```text
Working with JSON:API
```

The serialized document, formatted for display:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    }
  }
}
```

Role annotations identify JSON:API members. Jackson still owns visibility, property names, creators,
mix-ins, and value conversion. Unannotated attributes do not join the mapping automatically.

## API reference and next steps

The `JsonApi` application API groups operations under `resources()`, `relationships()`,
`documents()`, and `patches()`. Continue with [resources](resources.md); use
[relationships and documents](relationships-and-documents.md#ordinary-or-advanced) to choose a lower-level path.

<!-- x-release-please-start-version -->
The [API Reference](api-reference.md) links rendered Javadoc for released artifacts, including
[`JsonApi`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/api/JsonApi.html)
and [`JsonApiJackson3`](https://www.javadocs.dev/com.kazforge/jsonapi-java-jackson3/0.4.0/com/kazforge/jsonapi/jackson3/JsonApiJackson3.html).
These links target 0.4.0, not `main`. Exhaustive method/overload contracts belong in Javadoc,
not in this guide.
<!-- x-release-please-end -->
