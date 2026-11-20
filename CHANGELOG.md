# Changelog

Consumer-visible changes for the unified release train shared by the
`com.kazforge:jsonapi-java-*` artifacts. Version and change semantics follow
[ADR-014](docs/adr/014-unified-release-train.md); release-please maintains future
entries. Tags are named `v<version>`.

## [0.6.0](https://github.com/kazforge/jsonapi-java/compare/v0.5.0...v0.6.0) (2026-11-20)


### ⚠ BREAKING CHANGES

* **jackson:** consolidate internal mapping into jackson 3
* **jackson:** the Jackson 2 adapter is no longer published; use jsonapi-java-jackson3.
* **api:** artifact com.kazforge:jsonapi-java-jackson-api is replaced by com.kazforge:jsonapi-java-api. all public types moved from com.kazforge.jsonapi.jackson.* to com.kazforge.jsonapi.*.
* **jackson:** RelationshipLinkageMapper moves to the public mapping subpackage in both adapters with no forwarding alias.
* **core:** ValidationContext and JsonApiDocumentValidator move from core.validation to core.aggregate.
* the public package base is now com.kazforge.jsonapi and the Maven group is com.kazforge; artifact IDs remain jsonapi-java-* and no compatibility aliases are provided.
* Link.ObjectLink.describedby is now a nested Link, and ValidationRuleCode.RELATIONSHIP_PAGINATION_REQUIRES_HINT is removed.
* **jackson3:** public advanced names changed and the no-argument ResourceTypeRegistry.builder() was removed without compatibility aliases.
* **jackson-api:** separate representation selection from policy
* **jackson:** artifact io.github.kazemek:jsonapi-java-jackson-common is replaced by io.github.kazemek:jsonapi-java-jackson-api; all public types moved from io.github.kazemek.jsonapi.jackson to io.github.kazemek.jsonapi.jackson.{document,mapping,patch, representation,diagnostic}
* **jackson3:** Jackson 3 adapter factories now accept configured JsonMapper instances rather than JsonMapper.Builder values.
* encapsulate sparse-fieldset provenance at the writer boundary
* **jackson-common:** `io.github.kazemek.jsonapi.jackson3` codec/mapping policy types moved to `io.github.kazemek.jsonapi.jackson` in `jsonapi-java-jackson-common` without compatibility wrappers.
* **jackson3:** CompoundSerializationContext gains fieldsets and FieldPolicy; the prior four-argument canonical constructor is removed.

### Features

* **annotations:** add dependency-free JSON:API role metadata for domain mapping ([f96e24d](https://github.com/kazforge/jsonapi-java/commit/f96e24dcae64aa25a55b07096d4d5406ddc24931))
* **api:** add create-aware resource DTO reads ([08bdd83](https://github.com/kazforge/jsonapi-java/commit/08bdd83e49a383b2c6cac45372dcbf13edbc3870))
* **core:** add error document builders ([3b13fb9](https://github.com/kazforge/jsonapi-java/commit/3b13fb90cff48c6b65a92372953c1da3ace807dd))
* **core:** add focused record construction helpers ([9bd42cd](https://github.com/kazforge/jsonapi-java/commit/9bd42cdaede3823adf19b35c56df19668d486ddd))
* **core:** add JSON:API v1.1 document model and validation ([26b0896](https://github.com/kazforge/jsonapi-java/commit/26b0896f39f2da61895ba3a3e69e87b3913ce0a0))
* **core:** complete JSON:API create-request validation ([#147](https://github.com/kazforge/jsonapi-java/issues/147)) ([abc39ee](https://github.com/kazforge/jsonapi-java/commit/abc39eeedd365078bd4a1e58faf2e7f628869ff6))
* **core:** validate ErrorSource.pointer as RFC 6901 ([2f66507](https://github.com/kazforge/jsonapi-java/commit/2f66507e1ffbc33eb7094e730f33278172a5a31a))
* **core:** validate resource update requests ([4a55d2e](https://github.com/kazforge/jsonapi-java/commit/4a55d2e10b95fb1653adab7ab8283be4160c7793))
* **jackson-api:** add explicit resource type registry ([882fd74](https://github.com/kazforge/jsonapi-java/commit/882fd7467b2d44d2fed4f9e70c49349aadb00599))
* **jackson-api:** add first-class local identifier domain mapping ([1929db2](https://github.com/kazforge/jsonapi-java/commit/1929db20588689e6c0ba66847eb3c053b1752715))
* **jackson-api:** add resource link decoration for domain writes ([5c7ee01](https://github.com/kazforge/jsonapi-java/commit/5c7ee0188a35704d89fbabaddb3a1938feb28d6e))
* **jackson-api:** define major-neutral Level-1 application API contract ([488cc84](https://github.com/kazforge/jsonapi-java/commit/488cc844852e5645972ef3aaeb0d47cbb6611e64))
* **jackson-api:** separate representation selection from policy ([f35ff1d](https://github.com/kazforge/jsonapi-java/commit/f35ff1d2da411e7f4d069fc6e64525499acd3a01))
* **jackson-common:** extract neutral codec and mapping contracts ([668d21b](https://github.com/kazforge/jsonapi-java/commit/668d21bb0d89b6d1f2e3478bfc691c180dd4fe08))
* **jackson-common:** normalize mapping diagnostic location semantics ([29c121d](https://github.com/kazforge/jsonapi-java/commit/29c121d6dc575ff19f55eb77fc0278d1a0fef973))
* **jackson2:** add domain-to-resource resource mapper ([77cfe7e](https://github.com/kazforge/jsonapi-java/commit/77cfe7e16f9402fa4fdaed21db71c35ea2ee1b48))
* **jackson2:** add flat DTO resource binder ([976bff2](https://github.com/kazforge/jsonapi-java/commit/976bff27f7332d94e3f3b1bc303f13c051f4dceb))
* **jackson2:** add Level-1 application runtime ([2ac10f7](https://github.com/kazforge/jsonapi-java/commit/2ac10f751a3cc1238ae9da27354e860ae3530c39))
* **jackson2:** add module with validated document writer ([4e07252](https://github.com/kazforge/jsonapi-java/commit/4e07252abb352408827105737bf20c5f8a6c1923))
* **jackson2:** add presence-aware PATCH binding ([e8c3fc8](https://github.com/kazforge/jsonapi-java/commit/e8c3fc85a4c3519f5c951352ce56d6f2dda2db68))
* **jackson2:** add typed domain envelope reader ([4eb192b](https://github.com/kazforge/jsonapi-java/commit/4eb192b9afd83d1a14a43ab39832e7adee8fc481))
* **jackson2:** add validated document reader ([199eac9](https://github.com/kazforge/jsonapi-java/commit/199eac9188da1eda4003739b4284d4e04e6428e9))
* **jackson3:** add annotated domain-to-resource mapping ([0ff5e7f](https://github.com/kazforge/jsonapi-java/commit/0ff5e7f953e12ef54a999b7ab87e8a2c974fb718))
* **jackson3:** add compound serialization context ([a0e3e5b](https://github.com/kazforge/jsonapi-java/commit/a0e3e5b405ae6cb580f71f17dad80b01ae521af4))
* **jackson3:** add configured Level-1 JsonApi runtime ([f38e32f](https://github.com/kazforge/jsonapi-java/commit/f38e32f10ef6f38fc7481932109ac54ce5d191dd))
* **jackson3:** add direct typed PATCH DTO binding ([3f63625](https://github.com/kazforge/jsonapi-java/commit/3f6362568839449a1d59e5cb466c44eb198e95e8))
* **jackson3:** add opt-in jsonapi.version default ([cc7ad23](https://github.com/kazforge/jsonapi-java/commit/cc7ad2329090c8a8cd140cb03d81d70fd7d5cc11))
* **jackson3:** add presence-aware PATCH binding ([#88](https://github.com/kazforge/jsonapi-java/issues/88)) ([11224e1](https://github.com/kazforge/jsonapi-java/commit/11224e119e1d4de44e9acec12e90ce0a0f81703d))
* **jackson3:** add recursive structured value PATCH semantics ([7182d5f](https://github.com/kazforge/jsonapi-java/commit/7182d5ff8a5619cdd15a0ec9c147498ccc2364b9))
* **jackson3:** add sparse fieldsets on write mapping ([f41c845](https://github.com/kazforge/jsonapi-java/commit/f41c8453a6444938305d23052f3b84110b8ba228))
* **jackson3:** add typed domain envelope reader ([caa8f93](https://github.com/kazforge/jsonapi-java/commit/caa8f935d2d0c3357029b03af5a2c39c38444039))
* **jackson3:** add validated document reader with pointer diagnostics ([a393514](https://github.com/kazforge/jsonapi-java/commit/a3935144dc8e6b8ae9bf3a618ff0ea865d9cff61))
* **jackson3:** add validated document writer with shared fixtures ([3a5d615](https://github.com/kazforge/jsonapi-java/commit/3a5d6152500aab63760ba836a3fcd34144b6fe69))
* **jackson3:** bind validated resources to flat DTOs ([4d261ef](https://github.com/kazforge/jsonapi-java/commit/4d261ef428900cd332e842e5c3db5201f4bf84cc))
* **jackson3:** enforce deserialization direction for flat reads ([a6beb55](https://github.com/kazforge/jsonapi-java/commit/a6beb55c82416177b38847f16521e0de4a541bd4))
* **jackson3:** flat whole-object mapping for resource/relationship meta ([52e96d0](https://github.com/kazforge/jsonapi-java/commit/52e96d0b719473b16969283b9ba2d6a47deb66b5))
* **jackson3:** support parameterized generic domain writes ([ae28edc](https://github.com/kazforge/jsonapi-java/commit/ae28edc203a090e6da1a201e44c8354dd7f5b359))
* **jackson:** drop jackson 2 runtime support ([c7047f3](https://github.com/kazforge/jsonapi-java/commit/c7047f34cc9c6eb2ca9705dd2fa066d7995bcb6d))
* make JSON:API mapping annotations role-only ([b724845](https://github.com/kazforge/jsonapi-java/commit/b724845d9426a7dfbb038db89282ee3369495af1))
* map ResourceIdentifier meta via RelationshipLinkage ([5ee39c1](https://github.com/kazforge/jsonapi-java/commit/5ee39c18c72c453ddf519e9a0ffd717b57d3e787))
* **mapping:** add internal mapping module foundation ([71d3105](https://github.com/kazforge/jsonapi-java/commit/71d31057df1608fff3e923f12d5386e98201c03c))
* **mapping:** add source attribution to create and typed patch binding ([914601b](https://github.com/kazforge/jsonapi-java/commit/914601bd5935d5ea9c5e393594847896c9c606f2))
* **query:** add neutral query parameter parser ([f17cfff](https://github.com/kazforge/jsonapi-java/commit/f17cfffa33fe78aa62b34522c32863a36006efc3))
* **reader:** discard unknown members on read, keep writers strict ([13086ac](https://github.com/kazforge/jsonapi-java/commit/13086ace25234eebed555b05e94019874c26e17a))
* **release:** add SBOM and build provenance ([bda14ad](https://github.com/kazforge/jsonapi-java/commit/bda14ad452955a44e8618df9775951d8787a2b08))
* **release:** automate versioning and publish lifecycle ([8584cbc](https://github.com/kazforge/jsonapi-java/commit/8584cbc785e9ed66617341a2b81ab26d46a47835))
* **validation:** refine aggregate validation context granularity ([cd839d6](https://github.com/kazforge/jsonapi-java/commit/cd839d6547566e56535262fc9189ca93441be22b))


### Bug Fixes

* address IntelliJ and NullAway lint warnings ([8d0860e](https://github.com/kazforge/jsonapi-java/commit/8d0860e7a1b54218b7341dcc7f5f7c9b4a18e492))
* address lint and build warnings ([a847f66](https://github.com/kazforge/jsonapi-java/commit/a847f66d6cb54a178d175b9152f26a270b74db27))
* clear Sonar smells and fail the gate on new issues ([6ba00ab](https://github.com/kazforge/jsonapi-java/commit/6ba00ab35a232c6a2b3818a617d291a1e0a65d93))
* **core:** reject cross-alias duplicates in identifier collections ([15f3103](https://github.com/kazforge/jsonapi-java/commit/15f3103a54965c0405852c003df06d6211cffc0a))
* **core:** reject invalid UTF-16 surrogate sequences in member names ([3cd5cd1](https://github.com/kazforge/jsonapi-java/commit/3cd5cd1e9bd83fc3428a400fcb52a7da6d9973c1))
* **core:** reject reserved link names in Links.additionalMembers ([14970a9](https://github.com/kazforge/jsonapi-java/commit/14970a9eeb03530ffdbc80b7ee0ae27a612eb898))
* correct JSON:API 1.1 conformance defects and harden codecs ([37d6a7d](https://github.com/kazforge/jsonapi-java/commit/37d6a7d6762ea6a90b72079cc1688eae18e12da5))
* **deps:** consolidate renovate updates and refresh verification metadata ([bc38e5d](https://github.com/kazforge/jsonapi-java/commit/bc38e5d9e2a0fd867791aae0a4f1428d46dca4fd))
* **deps:** consolidate renovate updates for spotless, nullaway, and archunit ([5e063e8](https://github.com/kazforge/jsonapi-java/commit/5e063e83f21eeca5cdbe71c90800381f5225fdc7))
* **deps:** separate Jackson baselines from current test versions ([d837fba](https://github.com/kazforge/jsonapi-java/commit/d837fbaa73d92d4c083074a866ead8bf97bf74db))
* **deps:** update dependency com.diffplug.spotless:com.diffplug.spotless.gradle.plugin to v8.10.1 ([03ce4fc](https://github.com/kazforge/jsonapi-java/commit/03ce4fcecc394dbee851cb4b7cd618ea822cd165))
* **deps:** update dependency com.diffplug.spotless:com.diffplug.spotless.gradle.plugin to v8.10.2 ([7612eb1](https://github.com/kazforge/jsonapi-java/commit/7612eb15337af560cdb008c9094482aba866b9d9))
* **deps:** update dependency com.google.errorprone:error_prone_core to v2.50.0 ([cd40abf](https://github.com/kazforge/jsonapi-java/commit/cd40abf41756513dd87aaa43c450a06608f96c25))
* **deps:** update dependency com.networknt:json-schema-validator to v3.0.7 ([3012939](https://github.com/kazforge/jsonapi-java/commit/3012939d3023a26e648f5438e88c84c795841104))
* **deps:** update dependency com.networknt:json-schema-validator to v3.0.8 ([6c1201b](https://github.com/kazforge/jsonapi-java/commit/6c1201bd09910e02cf37f4cc6704ec4d16301311))
* **deps:** update dependency com.networknt:json-schema-validator to v3.0.8 ([83dcabf](https://github.com/kazforge/jsonapi-java/commit/83dcabf81fc759ee43064b6f95e2be3a9ed9612d))
* **deps:** update dependency com.tngtech.archunit:archunit to v1.5.0 ([c5b4c50](https://github.com/kazforge/jsonapi-java/commit/c5b4c500a8fb5f02b39b700e1bf8c0b01053251d))
* **deps:** update dependency com.uber.nullaway:nullaway to v0.13.8 ([e82929b](https://github.com/kazforge/jsonapi-java/commit/e82929b5dcaa16c7a96c5904012a3fe029e74241))
* **deps:** update dependency com.uber.nullaway:nullaway to v0.14.0 ([d07f42e](https://github.com/kazforge/jsonapi-java/commit/d07f42e16f5e41a7ba8580be74ee9d78537bf502))
* **deps:** update dependency com.uber.nullaway:nullaway to v0.14.1 ([092a889](https://github.com/kazforge/jsonapi-java/commit/092a889a38b3af4eac3dbd7517d0a2d9b9a9f06e))
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.18.12 ([#90](https://github.com/kazforge/jsonapi-java/issues/90)) ([5509407](https://github.com/kazforge/jsonapi-java/commit/55094076a6e4a1eae9d0db3f3613a98c6cbb5430))
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.18.13-jdk5 ([49ca923](https://github.com/kazforge/jsonapi-java/commit/49ca923a5522b2ec6f16be28c25023032cfb3675))
* **deps:** update dependency net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin to v5.1.1 ([3b6e5cf](https://github.com/kazforge/jsonapi-java/commit/3b6e5cfc5941e2d91eaeaf02c17436abea1a96a8))
* **deps:** update dependency net.ltgt.nullaway:net.ltgt.nullaway.gradle.plugin to v3.2.0 ([2fc46a5](https://github.com/kazforge/jsonapi-java/commit/2fc46a5c6cc293642dc78924d9593d8af6d6b27f))
* **deps:** update dependency net.ltgt.nullaway:net.ltgt.nullaway.gradle.plugin to v3.3.0 ([69c3f28](https://github.com/kazforge/jsonapi-java/commit/69c3f2837ec1b1f435c94c24f55862a24c69e1e6))
* **deps:** update dependency org.apache.groovy:groovy-all to v5.0.8 ([379346f](https://github.com/kazforge/jsonapi-java/commit/379346f4081b21be1a87d482aa5cd06c92c20765))
* **deps:** update dependency org.apache.groovy:groovy-all to v5.1.0 ([e483720](https://github.com/kazforge/jsonapi-java/commit/e4837204d543120d6cdb7b015d43c778525394c5))
* **deps:** update dependency org.apache.groovy:groovy-all to v5.1.1 ([4c20765](https://github.com/kazforge/jsonapi-java/commit/4c207658d491b866244f85e32a75ddbe99e1ceb3))
* **deps:** update dependency org.apache.groovy:groovy-json to v5.1.2 ([e0bd4a1](https://github.com/kazforge/jsonapi-java/commit/e0bd4a18adf16ff6970981a844b91d3224502dc8))
* **deps:** update dependency org.jspecify:jspecify to v1.0.1 ([45334af](https://github.com/kazforge/jsonapi-java/commit/45334af53cbec8f0158709b76fba94448a5ff8cf))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v6 ([22efab4](https://github.com/kazforge/jsonapi-java/commit/22efab49af144d87a349a677608d28949e81b3a7))
* **deps:** update dependency tools.jackson.core:jackson-databind to v3.2.2 ([718d576](https://github.com/kazforge/jsonapi-java/commit/718d576e32b04000034318a154671e8977dc16c3))
* **jackson3:** align typed patch creator-failure classification across majors ([fb300d1](https://github.com/kazforge/jsonapi-java/commit/fb300d1e70f6184c19a85e22baed72760ed2e9bc))
* **jackson3:** clear Sonar S135 in mapping property classification ([fb338e9](https://github.com/kazforge/jsonapi-java/commit/fb338e9954b917163e8d0a1484258c2eadd34078))
* **jackson3:** preserve property-scoped Jackson authority ([c08096c](https://github.com/kazforge/jsonapi-java/commit/c08096ccf5e929c31dd93a7cb4bb778b1a4f957a))
* **jackson3:** unify configured resource metadata authority ([3973a79](https://github.com/kazforge/jsonapi-java/commit/3973a79acb1731f4a71a5e4ee75fbbbd5bd85e7a))
* **patch:** deserialize typed values only once ([2fdf550](https://github.com/kazforge/jsonapi-java/commit/2fdf5501a793ef2baec3d189db4921f0cc8864f3))
* resolve Sonar new-code issues ([cde70f2](https://github.com/kazforge/jsonapi-java/commit/cde70f2de2be1f4d0c38e1d369bc6cbf8fbe125d))
* **validation:** restrict top-level related links to relationship responses ([838b516](https://github.com/kazforge/jsonapi-java/commit/838b516a12db4969d005850803b2d0e377c63379))
* **validation:** scope create identity exception to the resource being created ([f13eb74](https://github.com/kazforge/jsonapi-java/commit/f13eb74692e1c35dc01e3467df854954d2df2cd1))


### Code Refactoring

* **api:** rename jackson-api artifact and packages to neutral api ([4520410](https://github.com/kazforge/jsonapi-java/commit/4520410bc33696a32d4a209cccf930d63243564c))
* **core:** move aggregate validation into core.aggregate ([a2d1b0d](https://github.com/kazforge/jsonapi-java/commit/a2d1b0d6545402e740fa7c62618ebbdaedd512d9))
* encapsulate sparse-fieldset provenance at the writer boundary ([ff834f8](https://github.com/kazforge/jsonapi-java/commit/ff834f8b0b0c04e5e4b44acdb7bc5235e47078f9))
* **jackson3:** align advanced capability API before Jackson 2 ([af6917c](https://github.com/kazforge/jsonapi-java/commit/af6917c8d35ca5611d5b97ac10ede1fb291aca16))
* **jackson3:** define mapper-based adapter construction policy ([ffd7ef2](https://github.com/kazforge/jsonapi-java/commit/ffd7ef28c01ccb45f299a3031b89eab03345eedf))
* **jackson:** consolidate internal mapping into jackson 3 ([d1a14a7](https://github.com/kazforge/jsonapi-java/commit/d1a14a7d8e3efd32cfc1e0e0475e0ba177b54eaa))
* **jackson:** remove adapter dependency cycles ([006c833](https://github.com/kazforge/jsonapi-java/commit/006c8331348e601676fdbce8e0cd65376bcc197e))
* **jackson:** rename jackson-common to jackson-api and reorganize public contracts ([68972e0](https://github.com/kazforge/jsonapi-java/commit/68972e0adbde09262f4ba8664d72f3f45dad46cb))
* migrate Maven group and Java packages to com.kazforge ([856e4e7](https://github.com/kazforge/jsonapi-java/commit/856e4e748f90531071bd8d1a82a4a0c147f87cb0))

## [0.5.0](https://github.com/kazforge/jsonapi-java/compare/v0.4.0...v0.5.0) (2026-11-20)


### Features

* **mapping:** add source attribution to create and typed patch binding ([914601b](https://github.com/kazforge/jsonapi-java/commit/914601bd5935d5ea9c5e393594847896c9c606f2))


## [0.4.0](https://github.com/kazforge/jsonapi-java/compare/v0.3.0...v0.4.0) (2026-10-16)


### Features

* **api:** add create-aware resource DTO reads ([08bdd83](https://github.com/kazforge/jsonapi-java/commit/08bdd83e49a383b2c6cac45372dcbf13edbc3870))

## [0.3.0](https://github.com/kazforge/jsonapi-java/compare/v0.2.0...v0.3.0) (2026-10-15)


### ⚠ BREAKING CHANGES

* **jackson:** consolidate internal mapping into jackson 3
* **jackson:** the Jackson 2 adapter is no longer published; use jsonapi-java-jackson3.

### Features

* **core:** add focused record construction helpers ([9bd42cd](https://github.com/kazforge/jsonapi-java/commit/9bd42cdaede3823adf19b35c56df19668d486ddd))
* **jackson:** drop jackson 2 runtime support ([c7047f3](https://github.com/kazforge/jsonapi-java/commit/c7047f34cc9c6eb2ca9705dd2fa066d7995bcb6d))


### Bug Fixes

* **jackson3:** align typed patch creator-failure classification across majors ([fb300d1](https://github.com/kazforge/jsonapi-java/commit/fb300d1e70f6184c19a85e22baed72760ed2e9bc))


### Code Refactoring

* **jackson:** consolidate internal mapping into jackson 3 ([d1a14a7](https://github.com/kazforge/jsonapi-java/commit/d1a14a7d8e3efd32cfc1e0e0475e0ba177b54eaa))

## [0.2.0](https://github.com/kazforge/jsonapi-java/releases/tag/v0.2.0) (2026-10-08)

First public release of jsonapi-java, a JSON:API 1.1 library for Java 21+.
This pre-1.0 release is intended for real use; the public API may evolve before
1.0. Minor releases may break compatibility; patch releases do not.

No migration from an earlier public coordinate is required: no earlier version
of these artifacts was publicly released. Pre-public package and artifact
renames are not consumer breaking changes.

### Highlights

* Native Jackson 2 and Jackson 3 runtimes for reading and writing JSON:API documents.
* Document validation, application DTO mapping, relationships, and compound documents.
* Presence-aware PATCH binding that distinguishes absent members, explicit null, and empty values.
* Sparse fieldsets and neutral query-parameter parsing.
* One release train under `com.kazforge`, with Java packages under `com.kazforge.jsonapi`.

Endpoints, persistence, authorization, query execution, and applying updates
remain application responsibilities. Spring integration is not included.

See the [user guide](https://jsonapi.kazforge.com/) for installation and usage.
