# Changelog

## [0.2.1](https://github.com/kazforge/jsonapi-java/compare/v0.2.0...v0.2.1) (2026-10-09)


### Bug Fixes

* **deps:** update dependency com.diffplug.spotless:com.diffplug.spotless.gradle.plugin to v8.10.4 ([ddd8b37](https://github.com/kazforge/jsonapi-java/commit/ddd8b37ed3fd1433a25a764b442c6ff81f7f59ab))


### Documentation

* reflect the first public 0.2.0 release ([009b66e](https://github.com/kazforge/jsonapi-java/commit/009b66ef073dd321ee1d512afd67d2ccc0e11b57))

## [0.2.0](https://github.com/kazforge/jsonapi-java/compare/v0.1.0...v0.2.0) (2026-10-08)

First public production-oriented release of jsonapi-java.
This is a pre-1.0 release: it is intended for real use, while the public API may still evolve based on consumer feedback before 1.0.

### ⚠ BREAKING CHANGES

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

* **agent:** add on-demand milestone reviews so agents can audit work against milestone contracts without altering canonical docs ([7f18e4f](https://github.com/kazforge/jsonapi-java/commit/7f18e4fe02544055b954125faea40bec9a4b1cc4))
* **annotations:** add dependency-free JSON:API role metadata for domain mapping ([f96e24d](https://github.com/kazforge/jsonapi-java/commit/f96e24dcae64aa25a55b07096d4d5406ddc24931))
* **core:** add error document builders ([3b13fb9](https://github.com/kazforge/jsonapi-java/commit/3b13fb90cff48c6b65a92372953c1da3ace807dd))
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
* make JSON:API mapping annotations role-only ([b724845](https://github.com/kazforge/jsonapi-java/commit/b724845d9426a7dfbb038db89282ee3369495af1))
* map ResourceIdentifier meta via RelationshipLinkage ([5ee39c1](https://github.com/kazforge/jsonapi-java/commit/5ee39c18c72c453ddf519e9a0ffd717b57d3e787))
* **mapping:** add internal mapping module foundation ([71d3105](https://github.com/kazforge/jsonapi-java/commit/71d31057df1608fff3e923f12d5386e98201c03c))
* **milestones:** add isolated design review before plan-review ([3e078ca](https://github.com/kazforge/jsonapi-java/commit/3e078ca5394152373a3e9a14f584ef9180f30d61))
* **query:** add neutral query parameter parser ([f17cfff](https://github.com/kazforge/jsonapi-java/commit/f17cfffa33fe78aa62b34522c32863a36006efc3))
* **reader:** discard unknown members on read, keep writers strict ([13086ac](https://github.com/kazforge/jsonapi-java/commit/13086ace25234eebed555b05e94019874c26e17a))
* **release:** add SBOM and build provenance ([bda14ad](https://github.com/kazforge/jsonapi-java/commit/bda14ad452955a44e8618df9775951d8787a2b08))
* **release:** automate versioning and publish lifecycle ([8584cbc](https://github.com/kazforge/jsonapi-java/commit/8584cbc785e9ed66617341a2b81ab26d46a47835))
* **test-fixtures:** add capability-tagged codec fixture contract ([f6d2e52](https://github.com/kazforge/jsonapi-java/commit/f6d2e52b5a7dbb7e5376651be98b2c207a341059))
* **test-fixtures:** add shared compound-write fixture catalog ([8c94e3a](https://github.com/kazforge/jsonapi-java/commit/8c94e3a4185ecdc6bef3c202bccfa81ee2cbb1dc))
* **test-fixtures:** add shared domain-read binder catalog ([c4095b7](https://github.com/kazforge/jsonapi-java/commit/c4095b75df7d4cc3211dcfd2a5827e08cf3cf4bf))
* **test-fixtures:** add shared domain-write fixture catalog ([22358a8](https://github.com/kazforge/jsonapi-java/commit/22358a84d4115fd6eff79a34b07abc3627b52d3e))
* **test-fixtures:** add shared envelope-read fixture catalog ([e9ce3aa](https://github.com/kazforge/jsonapi-java/commit/e9ce3aa10740f131842522750039abf9c24214c9))
* **test-fixtures:** add shared sparse-fieldset fixture catalog ([01a57df](https://github.com/kazforge/jsonapi-java/commit/01a57dfe29e2c8b713285afb17e1c6643de3b647))
* **test-fixtures:** convert codec fixtures from Groovy to Java ([100a8c3](https://github.com/kazforge/jsonapi-java/commit/100a8c3a6856975b40127150d5e97fd7bb976b87))
* **test-fixtures:** unify scenario retrieval across fixture catalog ([c674c8c](https://github.com/kazforge/jsonapi-java/commit/c674c8cce4c9bb81aca372c45a94e506ede45847))
* **validation:** refine aggregate validation context granularity ([cd839d6](https://github.com/kazforge/jsonapi-java/commit/cd839d6547566e56535262fc9189ca93441be22b))
* **workflow:** add implement-milestone skill with isolated review ([98e2cea](https://github.com/kazforge/jsonapi-java/commit/98e2ceab44c9ea104d3811c7b3f4647eb7c112ca))
* **workflow:** add milestone-plan-review skill with isolated review ([e6aec19](https://github.com/kazforge/jsonapi-java/commit/e6aec1928c5a01631c0d8fe73b8e3a81682d4d5f))


### Bug Fixes

* address IntelliJ and NullAway lint warnings ([8d0860e](https://github.com/kazforge/jsonapi-java/commit/8d0860e7a1b54218b7341dcc7f5f7c9b4a18e492))
* address lint and build warnings ([a847f66](https://github.com/kazforge/jsonapi-java/commit/a847f66d6cb54a178d175b9152f26a270b74db27))
* **build:** fingerprint shared fixture directories as Test inputs ([086e01a](https://github.com/kazforge/jsonapi-java/commit/086e01ae56b2001ca77a93a42deb9d0eb3591e54))
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
* **gradle:** pin Gradle 9.8.1 source checksum ([7cbeae3](https://github.com/kazforge/jsonapi-java/commit/7cbeae3dcbb5e12c75484e4d64b97c0fe076c38a))
* **jackson3:** clear Sonar S135 in mapping property classification ([fb338e9](https://github.com/kazforge/jsonapi-java/commit/fb338e9954b917163e8d0a1484258c2eadd34078))
* **jackson3:** preserve property-scoped Jackson authority ([c08096c](https://github.com/kazforge/jsonapi-java/commit/c08096ccf5e929c31dd93a7cb4bb778b1a4f957a))
* **jackson3:** unify configured resource metadata authority ([3973a79](https://github.com/kazforge/jsonapi-java/commit/3973a79acb1731f4a71a5e4ee75fbbbd5bd85e7a))
* **patch:** deserialize typed values only once ([2fdf550](https://github.com/kazforge/jsonapi-java/commit/2fdf5501a793ef2baec3d189db4921f0cc8864f3))
* **quality:** resolve Sonar new-code issues and IntelliJ lint findings ([6614eb0](https://github.com/kazforge/jsonapi-java/commit/6614eb0cf9af5ffae8485179b04c9b43e46ca5ec))
* resolve Sonar new-code issues ([cde70f2](https://github.com/kazforge/jsonapi-java/commit/cde70f2de2be1f4d0c38e1d369bc6cbf8fbe125d))
* **sonar:** clear empty type-token smell and enforce zero issues in CI ([1c4d85c](https://github.com/kazforge/jsonapi-java/commit/1c4d85c38e9a02119d43514fd6ac31e44cad7a56))
* **validation:** restrict top-level related links to relationship responses ([838b516](https://github.com/kazforge/jsonapi-java/commit/838b516a12db4969d005850803b2d0e377c63379))
* **validation:** scope create identity exception to the resource being created ([f13eb74](https://github.com/kazforge/jsonapi-java/commit/f13eb74692e1c35dc01e3467df854954d2df2cd1))


### Documentation

* add architecture overview and finish KAZ-88 cleanup ([a56ed23](https://github.com/kazforge/jsonapi-java/commit/a56ed23bdf898515570d8a89e82608810a63db2d))
* add compact agent discovery index ([98a50da](https://github.com/kazforge/jsonapi-java/commit/98a50da5185c1972acd1bec4ff5b7e7c21aa330d))
* add MkDocs site infrastructure ([73aa957](https://github.com/kazforge/jsonapi-java/commit/73aa957efe60d385ff5941263eb1500b4640f5dd))
* add security reporting and contributing guides ([17e7183](https://github.com/kazforge/jsonapi-java/commit/17e71831ab8ba3342b3f6ae46f44b1a78840a5bd))
* add the user guide and simplify repository readmes ([a90e382](https://github.com/kazforge/jsonapi-java/commit/a90e38293cbfbdfed56c3ce7edca1cc6acc81065))
* address Phase 1.3 review feedback ([9124951](https://github.com/kazforge/jsonapi-java/commit/912495114a76c7802ffd825c86e44bbc38a0e43f))
* **adr:** consolidate decisions to the current durable set ([ec30c35](https://github.com/kazforge/jsonapi-java/commit/ec30c35f6b0a9d9c86813b8846b6ba5fe84414ad))
* **adr:** decide domain relationship data-presence model (ADR-018) ([3ac3733](https://github.com/kazforge/jsonapi-java/commit/3ac37339390b97e1d7e8044f0d26f7b22e422d37))
* **adr:** renumber retained ADRs sequentially ([096faa8](https://github.com/kazforge/jsonapi-java/commit/096faa804f9726881158d317c84c77505e3c28b5))
* **agents:** close router gaps so agents discover skills and skip needless milestones ([2eedc28](https://github.com/kazforge/jsonapi-java/commit/2eedc28dfc071e3855a9124351e719cdff85ee4a))
* **agents:** enable targeted module discovery without repo-wide scans ([389988e](https://github.com/kazforge/jsonapi-java/commit/389988ebd28c21240798e1b2fe62f89f08cf0bce))
* **agents:** unify task-scoped discovery and module-doc pattern ([996468b](https://github.com/kazforge/jsonapi-java/commit/996468bdff83103459fc40c16a972b018ab8f636))
* apply spotless before build in completion gates ([73e3717](https://github.com/kazforge/jsonapi-java/commit/73e37172f4dd3b766a023e40906b0c061a0f846a))
* **architecture:** record responsibility-based mapping and native codecs ([2aca940](https://github.com/kazforge/jsonapi-java/commit/2aca940acef0fa834d3d4ee849eb635d2b419f27))
* clarify public API ownership and release review policy ([ee7d6a0](https://github.com/kazforge/jsonapi-java/commit/ee7d6a0d7fd4992d9c500004b703ce9ca5f3338b))
* clarify structured attribute PATCH boundaries ([e61d4fb](https://github.com/kazforge/jsonapi-java/commit/e61d4fb58acf3a055a3789e589c29dcf7c01cedd))
* cut over to disposable implementation plans ([221b0ae](https://github.com/kazforge/jsonapi-java/commit/221b0aecfe1af68bc039a73647209463f531af4c))
* document runtime and per-operation configuration ([42dae97](https://github.com/kazforge/jsonapi-java/commit/42dae97afd8a1a1c529c2bf73ddfb1452d5bfef6))
* establish vision, milestones, ADRs, and agent workflow ([a5dd57b](https://github.com/kazforge/jsonapi-java/commit/a5dd57b4a880e4f96e38b59c06b66dae040abce9))
* explain diagnostics and JSON:API error attribution ([7798f8f](https://github.com/kazforge/jsonapi-java/commit/7798f8fd828a59a26deafea609842e594b257e60))
* improve search-engine discoverability ([159cc40](https://github.com/kazforge/jsonapi-java/commit/159cc401916343dd635cf92d992ef86af95ed198))
* introduce snapshot and outlook knowledge model ([382f1c8](https://github.com/kazforge/jsonapi-java/commit/382f1c8175d3869975869b5f5bed3124b9ebd882))
* **jackson-api:** frame public contracts as backend-neutral ([dfbdeb2](https://github.com/kazforge/jsonapi-java/commit/dfbdeb21430da43fdd23dc35a6094a7db196093a))
* **jackson3:** clarify domain mapping usage examples ([993e037](https://github.com/kazforge/jsonapi-java/commit/993e037fe4096266c1c1a43e31b55f79f740f210))
* migrate snapshot knowledge off completed milestones ([615612e](https://github.com/kazforge/jsonapi-java/commit/615612e5c9b470a11216dbb80fbc10c700fd2d67))
* **milestones:** lock phase 1.2 metadata boundaries for mapping handoff ([efd56d8](https://github.com/kazforge/jsonapi-java/commit/efd56d867aa3cdc913990ef8b3e8358b5ac07819))
* **milestones:** pin Phase 2.15 Jackson 3 PATCH binding ([67a1f93](https://github.com/kazforge/jsonapi-java/commit/67a1f93715b451f9da36c88054418f6e99eb22cf))
* **milestones:** plan java fixture conversion and scenario retrieval ([dd76fe6](https://github.com/kazforge/jsonapi-java/commit/dd76fe6c4d2a3abed59129cffdf03ad8fddac920))
* **milestones:** refine phase 1.2 annotation contract ([e1293d8](https://github.com/kazforge/jsonapi-java/commit/e1293d8516218e52f7e10bd52183f06295b4c5f1))
* **milestones:** refine phase 2.9 flat DTO reader contract ([677b586](https://github.com/kazforge/jsonapi-java/commit/677b586996769bf58cbd9e8538bd71a52ca4986f))
* **milestones:** refine shared domain-write fixtures ([7d99226](https://github.com/kazforge/jsonapi-java/commit/7d99226206aa260fee44b56ed0ff0626033e307d))
* note gitignored agent session artifacts ([2f6567a](https://github.com/kazforge/jsonapi-java/commit/2f6567a11f446c6a8a37b29e4dadd3c59017f42d))
* **planning:** finalize KAZ-18 semantic fixture plans ([4e115de](https://github.com/kazforge/jsonapi-java/commit/4e115defcd47b3dc71b1bc753efcd88370f91265))
* record unified release train and version semantics ([5ff102b](https://github.com/kazforge/jsonapi-java/commit/5ff102b06e3c25ae1f1a61c3384a8cd9804632b1))
* reduce package javadoc and module readme duplication ([61b29f2](https://github.com/kazforge/jsonapi-java/commit/61b29f2b1eeb68038c1d2e36f3f2199aab015baf))
* refine Phase 2.10 typed domain envelope milestone ([a90c01b](https://github.com/kazforge/jsonapi-java/commit/a90c01b1cf82d801e7c707bb1638ae433ae481d1))
* refine Phase 2.3 compound serialization milestone ([3ff523f](https://github.com/kazforge/jsonapi-java/commit/3ff523fd88789947cd751996bdf9fc75fc240387))
* refine Phase 2.8 sparse fieldsets milestone ([d02d46c](https://github.com/kazforge/jsonapi-java/commit/d02d46c3ea4ef990e8c994d438f09aace80bb91f))
* **release:** synchronize guide versions with release please ([09ae8c2](https://github.com/kazforge/jsonapi-java/commit/09ae8c2dcbb09c6ddb9abd0546874f896a4be46b))
* remove issue-tracker references from durable documentation ([a1148d1](https://github.com/kazforge/jsonapi-java/commit/a1148d1e9a1c5969c2a7dd0a935d52e570ac8e01))
* renumber ADRs to close gap left by deleted ADR-008 ([77392d5](https://github.com/kazforge/jsonapi-java/commit/77392d53084cab4f323e8a32a51fc47b5599e3fe))
* require Sonar Issues API for zero new issues ([b7f5dab](https://github.com/kazforge/jsonapi-java/commit/b7f5dab44f7ece1486c77a3a05dc868c861bbdbe))
* resequence phase 2 for common contracts and fixtures ([d6563c4](https://github.com/kazforge/jsonapi-java/commit/d6563c47e64113ec4df882f0c3b7a4a875d50ccb))
* **roadmap:** plan DTO-first binding and PATCH semantics ([71ff268](https://github.com/kazforge/jsonapi-java/commit/71ff26875d1bd5ee486bfde6fc11f80bb42bfa9a))
* **roadmap:** plan Phase 1.3 update request validation ([33676cf](https://github.com/kazforge/jsonapi-java/commit/33676cf9558c0127cdaec3fb79145bf595ff956b))
* streamline maintainer documentation ([9795c75](https://github.com/kazforge/jsonapi-java/commit/9795c75d1b469c6b7fbc57e623356d224fcbdff7))
* tighten consumer Javadoc and enforce Javadoc builds ([4f4e0c2](https://github.com/kazforge/jsonapi-java/commit/4f4e0c261b80dba6530e574be440ce59a122dfc7))
* update site URL in MkDocs configuration ([a2f32ba](https://github.com/kazforge/jsonapi-java/commit/a2f32baee74f12ea9a71812c8f36b19bf46eec24))


### Code Refactoring

* **api:** rename jackson-api artifact and packages to neutral api ([4520410](https://github.com/kazforge/jsonapi-java/commit/4520410bc33696a32d4a209cccf930d63243564c))
* **core:** move aggregate validation into core.aggregate ([a2d1b0d](https://github.com/kazforge/jsonapi-java/commit/a2d1b0d6545402e740fa7c62618ebbdaedd512d9))
* encapsulate sparse-fieldset provenance at the writer boundary ([ff834f8](https://github.com/kazforge/jsonapi-java/commit/ff834f8b0b0c04e5e4b44acdb7bc5235e47078f9))
* **jackson3:** align advanced capability API before Jackson 2 ([af6917c](https://github.com/kazforge/jsonapi-java/commit/af6917c8d35ca5611d5b97ac10ede1fb291aca16))
* **jackson3:** define mapper-based adapter construction policy ([ffd7ef2](https://github.com/kazforge/jsonapi-java/commit/ffd7ef28c01ccb45f299a3031b89eab03345eedf))
* **jackson:** remove adapter dependency cycles ([006c833](https://github.com/kazforge/jsonapi-java/commit/006c8331348e601676fdbce8e0cd65376bcc197e))
* **jackson:** rename jackson-common to jackson-api and reorganize public contracts ([68972e0](https://github.com/kazforge/jsonapi-java/commit/68972e0adbde09262f4ba8664d72f3f45dad46cb))
* migrate Maven group and Java packages to com.kazforge ([856e4e7](https://github.com/kazforge/jsonapi-java/commit/856e4e748f90531071bd8d1a82a4a0c147f87cb0))

## Changelog

All notable changes to this repository are maintained by
[release-please](https://github.com/googleapis/release-please) as the single
release orchestrator, following
[ADR-014](docs/adr/014-unified-release-train.md).

Each entry below corresponds to one unified release train version shared by all
seven `com.kazforge:jsonapi-java-*` artifacts. Tags are named `v<version>`.

Release-please maintains this file from the next release onward. No releases
have been cut from this lifecycle yet.
