# Getting started

## Choose an artifact

Use Java 21 or newer. Pick **one** native Jackson adapter for the mapper your application already
uses; the library does not detect or switch Jackson majors at runtime.

| Artifact (`com.kazforge`) | When to use it |
|---------------------------|----------------|
| `jsonapi-java-jackson3` | Configured `tools.jackson.databind.json.JsonMapper` |
| `jsonapi-java-jackson2` | Configured `com.fasterxml.jackson.databind.json.JsonMapper` |
| `jsonapi-java-query` | Optional parsing of `include`, `fields`, `sort`, and opaque parameters |
| `jsonapi-java-core` | Immutable documents and validation without a JSON backend |
| `jsonapi-java-api` | Neutral `JsonApi`, mapping, representation, and PATCH contracts; no runtime by itself |
| `jsonapi-java-annotations` | Mapping role annotations without a runtime dependency |

An adapter brings API, annotations, and core transitively. Its `jsonapi-java-mapping` dependency is
an implementation artifact, not an API for application code. Packages containing `internal` are
also unsupported. Java visibility alone is not a compatibility promise.

Jackson 2 and 3 are separate supported lines. Concrete minimums and LTS baselines are maintained in
the [Jackson 2 support section](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-jackson2/README.md#jackson-support)
and [Jackson 3 support section](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-jackson3/README.md#jackson-support).
Compatible newer versions can be selected by your dependency management; the adapter does not
force a platform or override your application's Jackson version. That is not a tested Spring Boot matrix.

## Dependency declarations

`RELEASE_VERSION` below is a placeholder, **not a published version**. After a release exists,
replace it with a version verified in [Maven Central](https://central.sonatype.com/namespace/com.kazforge).
Use the same version for every `jsonapi-java-*` artifact; there is no BOM.

=== "Gradle Kotlin DSL"

    ```kotlin
    repositories { mavenCentral() }

    dependencies {
        implementation("com.kazforge:jsonapi-java-jackson3:RELEASE_VERSION")
        // Optional:
        implementation("com.kazforge:jsonapi-java-query:RELEASE_VERSION")
    }
    ```

=== "Maven"

    ```xml
    <properties>
        <jsonapi.version>RELEASE_VERSION</jsonapi.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>com.kazforge</groupId>
            <artifactId>jsonapi-java-jackson3</artifactId>
            <version>${jsonapi.version}</version>
        </dependency>
    </dependencies>
    ```

For Jackson 2, replace the adapter artifact with `jsonapi-java-jackson2`. Add `jsonapi-java-query`
separately if you need [query parsing](query-and-representation.md).

### Before the first release

No public artifact is available yet. With Git and a local JDK 21, install this checkout's artifacts
in your local Maven repository:

```bash
git clone https://github.com/kazforge/jsonapi-java.git
cd jsonapi-java
./gradlew publishToMavenLocal
```

Use the checkout's `version` from `gradle.properties` in place of `RELEASE_VERSION` (currently
`0.1.0`). This is a **local build**, not evidence that `0.1.0` was released. Maven reads the local
repository automatically; for a Gradle consumer, use:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

All artifacts use one version. Before 1.0, a breaking change ships in a minor release, never a
patch release. Review release notes when upgrading across minors. Raising a supported Java or
Jackson minimum is a breaking change too.

## Configure a runtime

Configure Jackson first, then pass the mapper to the matching factory. Keep the resulting immutable,
thread-safe runtime for your application's lifetime. Factories do not mutate your mapper.

=== "Jackson 3"

    ```java
    import com.kazforge.jsonapi.api.JsonApi;
    import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
    import tools.jackson.databind.json.JsonMapper;

    JsonMapper mapper = JsonMapper.builder().build();
    JsonApi api = JsonApiJackson3.builder(mapper).jsonApiVersion("1.1").build();
    ```

=== "Jackson 2"

    ```java
    import com.kazforge.jsonapi.api.JsonApi;
    import com.kazforge.jsonapi.jackson2.JsonApiJackson2;
    import com.fasterxml.jackson.databind.json.JsonMapper;

    JsonMapper mapper = JsonMapper.builder().build();
    JsonApi api = JsonApiJackson2.builder(mapper).jsonApiVersion("1.1").build();
    ```

The builder holds application policy, identifier conversion, linkage mappers, decorators, and an
optional resource-write `jsonapi.version`. Selection and document envelopes belong to each operation.
If you need only defaults, use `JsonApiJackson3.jsonApi(mapper)` or `JsonApiJackson2.jsonApi(mapper)`.
Emitting `jsonapi.version` is optional; it does not implement HTTP content negotiation.

## Read and write an article

Save this complete Jackson 3 example as `Example.java` in your consumer project and run its `main`
method. For Jackson 2, change only the two native-major imports and the factory call. Later pages
use Java assertions to show results; enable them with `-ea` when running those examples.

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import tools.jackson.databind.json.JsonMapper;

public class Example {
    @JsonApiResource(type = "articles")
    public record Article(@JsonApiId String id, @JsonApiAttribute String title) {}

    public static void main(String[] args) {
        JsonApi api = JsonApiJackson3.jsonApi(JsonMapper.builder().build());
        Article article = new Article("1", "Working with JSON:API");
        String json = api.resources().writeOne(article);
        Article readBack = api.resources().readOne(json, Article.class);
        if (!article.equals(readBack)) throw new AssertionError(readBack);
        System.out.println(json);
    }
}
```

Output:

```json
{"data":{"type":"articles","id":"1","attributes":{"title":"Working with JSON:API"}}}
```

Role annotations identify JSON:API members. Jackson still owns visibility, property names, creators,
mix-ins, and value conversion. Unannotated attributes do not join the mapping automatically.

## API reference and next steps

The guide examples use four neutral facets: `resources()`, `relationships()`, `documents()`, and
`patches()`. Continue with [resources](resources.md); use
[relationships and documents](relationships-and-documents.md#ordinary-or-advanced) to choose a lower-level path.

Until published Javadoc is available, the source Javadoc for
[`JsonApi`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/api/JsonApi.java),
[`JsonApiJackson3`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-jackson3/src/main/java/com/kazforge/jsonapi/jackson3/JsonApiJackson3.java),
and [`JsonApiJackson2`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiJackson2.java)
is the reference. The source-build command also generates HTML under each consumer-facing module's
`build/docs/javadoc/index.html` and publishes a Javadoc JAR locally. Exhaustive method/overload
contracts belong there, not in this guide.
