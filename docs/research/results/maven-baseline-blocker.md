# Desktop Maven cache blocker: acquisition proposal

2026-10-18, Windows host. **Historical read-only acquisition proposal.**

Historical proposal below. The owner subsequently approved the bounded cache
fill and unchanged offline baseline; [actual results](maven-baseline-results.md)
now verify both phases passed without source/POM changes. No extension followed.
Separate documentation branch `codex/maven-baseline-inventory`, based on PR
#17 head `08e42ffb7d6496dd85552ea6947938e2ad9944be`. The embedded-test extension
plan and its implementation remain untouched while Claude reviews them.

## Finding

The unchanged desktop test lifecycle requires GMaven **as configured**. The
Java embedded test itself does not use Groovy. Forge's original
[desktop POM:212](https://github.com/rwZephyr/forge-adventure-web/blob/db893dcdb14fdd85a05437ad458dc70ef704ca2e/forge-gui-desktop/pom.xml#L212)
binds `org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute` to `initialize`
with execution ID `get-changelog-from-tag`. Its inline script runs local Git
commands and sets `changelog.from.tag`; the changelog generator runs later at
`package`. This is unconditional in the normal desktop build, not confined to
a release profile. Forge's optional android-test-build profile changes the
changelog generator, not this GMaven execution; it is not a solution here.

Maven `test` includes earlier lifecycle phases, including initialize. Calling
Surefire directly or disabling this execution would bypass the original
lifecycle and is excluded. See [Maven's lifecycle definition](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html).
Moreover, the plugin's published [execute goal metadata](https://groovy.github.io/gmaven/groovy-maven-plugin/execute-mojo.html)
requires **test-scope dependency resolution**, even though its optional added
script classpath defaults to none. Resolving the plugin alone is insufficient
to prepare the unchanged desktop test.

The previous unchanged offline attempt stopped before desktop compilation or
test execution: plugin POM/JAR absent, exit 1, 33.786 seconds. Five preceding
modules passed. [Preserved log](../../experiments/evidence/embedded-baseline-20261018/maven-baseline.log).
No Maven/build/test invocation was repeated during this investigation.

## Exact cache observations

[Machine-readable inventory](evidence/maven-cache-inventory-20261018.json)
records coordinate-level POM/JAR presence, hashes for present files, other
versions, and an aggregate Maven cache content fingerprint. Observed
`.local-tools/m2`: **280 JARs and 528 POMs**. No GMaven or Groovy artifacts were
found in that Maven cache or the Gradle dependency cache. The Gradle distribution
does bundle Groovy **4.0.32**, which is not the plugin's requested groovy-all
2.4.9 and must not be substituted.

Confirmed missing starting artifacts:

```text
org/codehaus/gmaven/groovy-maven-plugin/2.1.1/groovy-maven-plugin-2.1.1.pom
org/codehaus/gmaven/groovy-maven-plugin/2.1.1/groovy-maven-plugin-2.1.1.jar
org/codehaus/gmaven/gmaven/2.1.1/gmaven-2.1.1.pom
com/planet57/buildsupport/public-parent/2/public-parent-2.pom
```

The first parent is identified by the [published plugin POM](https://central.sonatype.com/artifact/org.codehaus.gmaven/groovy-maven-plugin),
and the second by [GMaven's published parent POM](https://central.sonatype.com/artifact/org.codehaus.gmaven/gmaven).
These parent artifacts are POM-only; do not request nonexistent parent JARs.
Additional inherited/imported/parent POMs may be needed. Their complete closure
has not been resolved, so this is not a four-file acquisition claim.

The [publisher's 2.1.1 dependency report](https://groovy.github.io/gmaven/groovy-maven-plugin/dependencies.html)
and [adapter dependency report](https://groovy.github.io/gmaven/gmaven-adapter-impl/dependencies.html)
identify the following expected plugin compile/runtime coordinates. Every JAR
in this table is absent; all their POMs are absent except jsr305:2.0.1.

| Expected coordinate | Role |
| --- | --- |
| org.codehaus.gmaven:groovy-maven-plugin:2.1.1 | Plugin |
| org.codehaus.gmaven:gmaven-adapter-api:2.1.1 | Adapter |
| org.codehaus.gmaven:gmaven-adapter-impl:2.1.1 | Adapter |
| org.codehaus.groovy:groovy-all:2.4.9 | Groovy runtime |
| org.slf4j:slf4j-api:1.7.5 | Logging API |
| org.sonatype.gossip:gossip-slf4j:1.8 | Logging binding |
| org.sonatype.gossip:gossip-core:1.8 | Logging transitive |
| org.sonatype.gossip:gossip-bootstrap:1.8 | Logging transitive |
| com.google.guava:guava:14.0.1 | Utility library |
| com.google.code.findbugs:jsr305:2.0.1 | Direct optional annotations; POM cached, JAR absent |
| org.codehaus.plexus:plexus-classworlds:2.4.2 | Classloading |
| org.codehaus.plexus:plexus-interpolation:1.16 | Interpolation |
| org.eclipse.aether:aether-util:0.9.0.M2 | Resolver utilities |
| org.eclipse.aether:aether-api:0.9.0.M2 | Resolver transitive |
| org.apache.ant:ant:1.9.4 | Adapter dependency |
| org.apache.ant:ant-launcher:1.9.4 | Adapter dependency |
| jline:jline:2.12 | Adapter dependency |

This is **17 absent expected JAR coordinates**, not a measured Maven download
count or an independently resolved plugin realm. Maven's runtime filtering,
provided APIs and metadata requirements determine the actual set. Do not
download every item on the publisher's dependency page: its Maven 3.0.5 APIs
and plugin annotations are marked provided; its JUnit/PowerMock/test-support
tree belongs to building/testing GMaven itself, not running Forge's script.
The plugin's own build/report plugins are likewise not Forge dependencies.

Other cached versions are not interchangeable: Maven cache has SLF4J 1.5.6,
1.7.32, 1.7.36 and 2.0.16; Guava 16.0.1, 33.3.1-android and 33.3.1-jre;
Plexus/Aether versions differ too. Installed portable Maven supplies its own
SLF4J 1.7.36. Preserve normal plugin realm resolution and Forge's existing
dependency versions; do not copy or rename these JARs to satisfy missing paths.

## Additional desktop dependencies hidden behind the first failure

The original desktop POM declares twelve external direct dependencies. Three
have both the exact POM and JAR cached: TestNG 7.10.2, Mockito 5.14.2 and
JCommander 1.82. The following **nine** have neither at their requested version:

| Coordinate | Scope |
| --- | --- |
| com.miglayout:miglayout-swing:4.2 | Compile |
| com.mortennobel:java-image-scaling:0.8.6 | Compile |
| net.bytebuddy:byte-buddy:1.18.14 | Test |
| net.bytebuddy:byte-buddy-agent:1.18.14 | Test |
| org.objenesis:objenesis:3.4 | Test |
| org.freemarker:freemarker:2.3.33 | Compile |
| com.googlecode.soundlibs:jlayer:1.0.1.4 | Compile |
| com.sipgate:mp3-wav:1.0.4 | Compile |
| com.twelvemonkeys.imageio:imageio-jpeg:3.12.0 | Compile |

Some older Byte Buddy/Objenesis versions are cached; the inventory lists them.
Selecting a single TestNG test still compiles the module's test sources and
resolves declared test dependencies. Unused Mockito-related dependencies cannot
be removed merely because this particular test does not mock anything.

Likely additional project artifacts include miglayout-core 4.2; filters
2.0.235; javazoom:jlayer 1.0.1; mp3spi 1.9.5.4 / tritonus-share 0.3.7.4; and
TwelveMonkeys imageio-core/imageio-metadata/common-lang/common-io/common-image
3.12.0, plus their parents. All these checked coordinate paths are absent.
The [java-image-scaling 0.8.6 POM](https://central.sonatype.com/artifact/com.mortennobel/java-image-scaling)
directly specifies filters 2.0.235. The [mp3-wav 1.0.4 POM](https://central.sonatype.com/artifact/com.sipgate/mp3-wav)
specifies javazoom:jlayer 1.0.1 and mp3spi 1.9.5.4. Forge excludes the
com.googlecode.soundlibs:jlayer branch from mp3-wav and supplies 1.0.1.4
directly, so its older 1.0.1-1 should not be added indiscriminately. The remaining
list is a candidate closure, not a completed version/exclusion/mediation audit;
exact historical POMs were not all available through metadata-page inspection.

Already cached: resources plugin 3.3.1, compiler 3.8.1, Surefire 3.1.2,
build-helper 3.6.0, Checkstyle plugin 3.1.2, and Surefire's TestNG provider/utils
3.1.2, each with its POM/JAR. The earlier five-module run also supports that
the inherited build machinery worked there. This does not prove every desktop
plugin transitive or dynamically selected provider is available. Neither the
dependency plugin nor help plugin is cached; adding one merely to fetch this
plugin would introduce another acquisition tool dependency.

## Smallest practical proposed acquisition and verification

**For owner approval only; neither command below was run this task.**
Recommend one normal online reactor pass through **test-compile**, restricted
to desktop and its prerequisite modules, then the unchanged embedded test
offline. This lets Maven acquire only the missing artifacts demanded by the
actual pre-test lifecycle, using the existing portable tools/cache. It runs
the original initialize script and compiles the module's tests; it does not
run tests during acquisition. It avoids package/install/deploy and broad
reporting-plugin acquisition. It is a practical bounded cache fill, not a claim
that a globally minimal number of downloaded bytes has been established.

1. Record source/POM/test hashes and a before cache manifest. Use existing JDK
   17.0.12 / portable Maven 3.9.16 and `.local-tools/m2`. Keep user.home,
   APPDATA, LOCALAPPDATA, TEMP/TMP and java.io.tmpdir isolated under the existing
   embedded-baseline directory as in the reviewed plan; restore process
   environment afterward. Do not alter system settings or a user Maven cache.
2. Permit Maven Central HTTPS (`https://repo.maven.apache.org/maven2`)
   acquisition of the missing desktop pre-test
   plugin/project dependency closure and associated POMs/checksums/metadata.
   Keep existing settings/profiles. No new repository/proxy, version override,
   profile workaround, `-U` refresh, plugin skip, or compatibility patch. If
   resolution requires another repository or a changed coordinate, stop for
   review. Preserve Maven's HTTP blocker.

   ```powershell
   & "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -B -ntp -T1 -C `
     "-Dmaven.repo.local=$repo/.local-tools/m2" `
     -pl forge-gui-desktop -am test-compile
   ```

3. Preserve the complete output. Require normal GMaven
   `get-changelog-from-tag` execution and desktop test compilation, with the
   original test class produced. Inventory actual new cache files, coordinates,
   byte sizes, SHA-256s, checksums and license declarations. Verify existing
   cache artifact hashes and original sources/POMs unchanged. `-C` requests
   strict repository checksum checking; local SHA-256 inventory records the
   bytes, not an independent publisher signature. Cache metadata/lastUpdated
   files may change and must be distinguished from artifact replacement.
4. Run the original baseline **offline**, with the same isolation and no
   extension implemented:

   ```powershell
   & "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -o -B -ntp -T1 `
     "-Dmaven.repo.local=$repo/.local-tools/m2" -pl forge-gui-desktop -am `
     '-Dtest=forge.net.embedded.ProtocolGuiGameInProcessTest' `
     '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false' test
   ```

5. Success requires the selected test actually executing with zero
   failures/errors/skips, its original gameplay/protocol assertions passing,
   no source/POM changes and no leftover fixture/evaluator. A reactor success
   with zero selected tests is insufficient. Cache misses or compiler/runtime
   failures are preserved and reported; no automatic online retry, dependency
   substitution, Groovy upgrade or Bolt work follows. JDK 17 execution of this
   old plugin and desktop bootstrap remains unverified, so acquisition cannot
   guarantee a passing test.

Using `dependency:go-offline` would be broader: its
[documented scope](https://maven.apache.org/plugins/maven-dependency-plugin/go-offline-mojo.html)
includes plugins, reports and their dependencies. Nor is a manual download of
only the GMaven JAR/POM reproducible: parent POMs, runtime dependencies and
desktop test dependencies are missing. The actual closure is finalized by the
approved lifecycle and successful offline verification, not by guessing a few
JARs or changing Forge's build.

**Approval requested:** this bounded online cache fill and unchanged offline
baseline verification only. No Bolt extension, browser/network listener,
installation, original-source adaptation, merge or service configuration
change. This report does not change PR #17 or its current implementation plan.
