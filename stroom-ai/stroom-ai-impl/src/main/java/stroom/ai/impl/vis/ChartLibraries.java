/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.vis;

import stroom.dashboard.impl.script.ScriptStore;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.script.shared.ScriptDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Finds the charting libraries installed in Stroom, and puts Stroom's ECharts adapter in place beside ECharts.
///
/// - ECharts is installed by an administrator as a Script doc holding an ECharts build, `echarts.min.js` (any name,
///   any folder). It is recognised by its content: the zrender dependency and version that each ECharts build
///   declares, version 6 or later. Any such build is used, so an administrator upgrades ECharts by putting a newer
///   build in the Script doc, with no new Stroom release. Who may edit that Script doc is a matter for its
///   permissions, as for any other visualisation script: its code runs, unsandboxed, in every generated chart.
/// - The adapter (`echarts-generic.js`, bundled here) is created as a chart is generated, as a Script doc in the
///   same folder as ECharts, depending on it, with the ECharts doc's permissions, so whoever can load ECharts can
///   load the adapter. It is named for its content (e.g. `ECharts Generic 3fa2c1`) and never changed, so the charts
///   drawn with it keep drawing as they did when Stroom is upgraded, and nodes of different versions don't undo
///   each other's; a new version of Stroom's adapter is created beside it as the next chart is generated. Nothing
///   is done as Stroom starts.
/// - D3 is installed the same way, as a Script doc holding a D3 build, version 3 or later (the content pack's 3.4.8
///   is one); the newest is used. Its adapter (`d3-generic.js`) is put beside it as ECharts' is.
/// - The content pack's own D3 Visualisations can also be wired to a table as they are (see [D3Visualisation]).
///
/// The search runs as the processing user, as Stroom loads a visualisation's scripts with elevated read; whether
/// the user may use what was found is reported separately.
@Singleton
public class ChartLibraries {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ChartLibraries.class);

    static final String ADAPTER_RESOURCE = "/stroom/ai/impl/vis/echarts-generic.js";
    static final String ADAPTER_NAME = "ECharts Generic";
    static final String ADAPTER_MARKER = "stroom-echarts-generic-version:";
    /// The oldest ECharts major version the adapter supports.
    public static final int MIN_ECHARTS_MAJOR = 6;

    // Each ECharts build declares the zrender it is built on, and its own version: `echarts.min.js` as
    // `t.version="6.1.0",...,t.dependencies={zrender:"6.1.0"}`, and `echarts.js` as
    // `var version$1 = '6.1.0';\n var dependencies = {\n zrender: '6.1.0'`. The bundled zrender's own version is
    // declared differently, so isn't taken for ECharts'.
    private static final Pattern ZRENDER_DEPENDENCY = Pattern.compile("dependencies\\s*=\\s*\\{\\s*zrender\\s*:");
    private static final Pattern MINIFIED_VERSION =
            Pattern.compile("[\\w$]\\.version\\s*=\\s*\"(\\d+)\\.(\\d+)\\.(\\d+)[^\"]*\"");
    private static final Pattern FULL_VERSION = Pattern.compile(
            "\\bversion[\\w$]*\\s*=\\s*['\"](\\d+)\\.(\\d+)\\.(\\d+)[^'\"]*['\"]\\s*;\\s*var\\s+dependencies\\s*=");

    // D3 4 and later begin with a banner naming their version: "// https://d3js.org v7.9.0" (or "Version 4.13.0").
    private static final Pattern D3_BANNER = Pattern.compile("d3js\\.org (?:Version |v)(\\d+)\\.(\\d+)\\.(\\d+)");
    // D3 3 declares its version as the d3 object's first property: `var d3 = {\n version: "3.4.8"` in the full build,
    // `var ao={version:"3.5.17"}` (and `this.d3=ao`) in the minified one.
    private static final Pattern D3_V3_VERSION = Pattern.compile("\\{\\s*version\\s*:\\s*\"(3)\\.(\\d+)\\.(\\d+)\"");
    private static final Pattern D3_V3_OBJECT = Pattern.compile("var\\s+d3\\s*=\\s*\\{\\s*version\\s*:");

    private final Provider<ScriptStore> scriptStoreProvider;
    private final Provider<VisualisationStore> visualisationStoreProvider;
    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<DocumentPermissionService> documentPermissionServiceProvider;
    private final SecurityContext securityContext;

    private final Map<ChartLibrary, String> adapterSources = new ConcurrentHashMap<>();

    @Inject
    public ChartLibraries(final Provider<ScriptStore> scriptStoreProvider,
                          final Provider<VisualisationStore> visualisationStoreProvider,
                          final Provider<ExplorerService> explorerServiceProvider,
                          final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                          final Provider<DocumentPermissionService> documentPermissionServiceProvider,
                          final SecurityContext securityContext) {
        this.scriptStoreProvider = scriptStoreProvider;
        this.visualisationStoreProvider = visualisationStoreProvider;
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.securityContext = securityContext;
    }

    /// Finds the ECharts library: a Script doc holding an ECharts build (version 6 or later), preferring one with
    /// "echarts" in its name. Where several are installed, the newest version wins.
    ///
    /// @return The library, if one is installed.
    public Optional<EChartsInstall> findECharts() {
        return securityContext.asProcessingUserResult(() -> {
            final ScriptStore scriptStore = scriptStoreProvider.get();
            final List<DocRef> scripts = NullSafe.list(scriptStore.list());
            final List<DocRef> named = scripts.stream()
                    .filter(ChartLibraries::namedECharts)
                    .toList();
            final Optional<EChartsInstall> byName = newest(scriptStore, named);
            if (byName.isPresent()) {
                return byName;
            }
            final List<DocRef> others = scripts.stream()
                    .filter(ref -> !named.contains(ref))
                    .toList();
            return newest(scriptStore, others);
        });
    }

    /// Finds Script docs named like ECharts that don't hold an ECharts 6 or later build, so the agent can say why
    /// ECharts isn't used (e.g. an ECharts 5 build, or something else).
    ///
    /// @return Those Script docs.
    public List<DocRef> findUnrecognisedECharts() {
        return securityContext.asProcessingUserResult(() -> {
            final ScriptStore scriptStore = scriptStoreProvider.get();
            return NullSafe.list(scriptStore.list()).stream()
                    .filter(ChartLibraries::namedECharts)
                    .filter(ref -> {
                        final ScriptDoc doc = read(scriptStore, ref);
                        return doc != null && adapterLibrary(doc.getData()).isEmpty()
                               && echartsVersion(doc.getData()).isEmpty();
                    })
                    .toList();
        });
    }

    /// Says whether a Visualisation is a chart Stroom AI generated: one drawn by Stroom's ECharts adapter, whose
    /// settings Stroom wrote and can write again. Content pack and hand-written Visualisations aren't.
    ///
    /// @param visualisation A Visualisation.
    /// @return True if Stroom AI generated it.
    public boolean isGenerated(final DocRef visualisation) {
        if (visualisation == null) {
            return false;
        }
        return securityContext.asProcessingUserResult(() -> {
            try {
                final VisualisationDoc doc = visualisationStoreProvider.get().readDocument(visualisation);
                return doc != null && ChartLibrary.forFunctionName(doc.getFunctionName()).isPresent();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Couldn't read visualisation {}: {}", visualisation,
                        e.getMessage()));
                return false;
            }
        });
    }

    /// Reads the chart a Visualisation Stroom AI generated draws: its kind and the column mapped to each of its
    /// roles, as its own settings give them (a vis component's settings may override them).
    ///
    /// @param visualisation A Visualisation.
    /// @return Each of its controls' default values, by control id (e.g. `kind`, and each role's id); empty if it
    /// isn't one Stroom AI generated, or can't be read.
    public Map<String, String> generatedChart(final DocRef visualisation) {
        if (visualisation == null) {
            return Map.of();
        }
        return securityContext.asProcessingUserResult(() -> {
            final VisualisationDoc doc = readVisualisation(visualisationStoreProvider.get(), visualisation);
            return doc != null && ChartLibrary.forFunctionName(doc.getFunctionName()).isPresent()
                    ? EChartsTemplate.defaults(doc.getSettings())
                    : Map.of();
        });
    }

    private static boolean namedECharts(final DocRef ref) {
        return NullSafe.string(ref.getName()).toLowerCase(Locale.ROOT).contains("echarts");
    }

    private Optional<EChartsInstall> newest(final ScriptStore scriptStore, final List<DocRef> refs) {
        EChartsInstall best = null;
        for (final DocRef ref : refs) {
            final ScriptDoc doc = read(scriptStore, ref);
            if (doc == null) {
                continue;
            }
            final Optional<String> version = echartsVersion(doc.getData());
            if (version.isPresent() && (best == null || compareVersions(version.get(), best.version()) > 0)) {
                best = new EChartsInstall(ref, version.get());
            }
        }
        return Optional.ofNullable(best);
    }

    private static ScriptDoc read(final ScriptStore scriptStore, final DocRef ref) {
        try {
            return scriptStore.readDocument(ref);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Couldn't read script {}: {}", ref, e.getMessage()));
            return null;
        }
    }

    /// Says which ECharts version a Script doc's content is, if it is an ECharts build of version 6 or later.
    ///
    /// @param source A Script doc's content.
    /// @return The version, e.g. `6.1.0`, if the content is such a build.
    static Optional<String> echartsVersion(final String source) {
        if (source == null || !ZRENDER_DEPENDENCY.matcher(source).find()) {
            return Optional.empty();
        }
        return version(MINIFIED_VERSION, source)
                .or(() -> version(FULL_VERSION, source));
    }

    private static Optional<String> version(final Pattern pattern, final String source) {
        final Matcher matcher = pattern.matcher(source);
        if (!matcher.find() || Integer.parseInt(matcher.group(1)) < MIN_ECHARTS_MAJOR) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3));
    }

    // Content as compared: line endings made \n, and leading and trailing white space removed, as editors change
    // them.
    static String normalise(final String source) {
        return source.replace("\r\n", "\n").strip();
    }

    static int compareVersions(final String a, final String b) {
        final String[] aParts = a.split("\\.");
        final String[] bParts = b.split("\\.");
        for (int i = 0; i < Math.max(aParts.length, bParts.length); i++) {
            final int aPart = i < aParts.length
                    ? Integer.parseInt(aParts[i])
                    : 0;
            final int bPart = i < bParts.length
                    ? Integer.parseInt(bParts[i])
                    : 0;
            if (aPart != bPart) {
                return Integer.compare(aPart, bPart);
            }
        }
        return 0;
    }

    /// Says whether the current user may use a Script doc, so a chart that needs it will load for them.
    ///
    /// @param ref The Script doc.
    /// @return True if they may.
    public boolean currentUserCanUse(final DocRef ref) {
        return securityContext.hasDocumentPermission(ref, DocumentPermission.USE);
    }

    /// Puts the adapter in place beside an ECharts library, as a chart is generated: finds the Script doc in the
    /// library's folder that holds this version of Stroom's adapter, depending on the library, or creates it if
    /// there isn't one. Either way it is given the library's permissions again, so whoever can load ECharts can load
    /// it.
    ///
    /// @param echarts The library.
    /// @return The adapter's Script doc.
    public DocRef ensureAdapter(final EChartsInstall echarts) {
        return ensureAdapter(ChartLibrary.ECHARTS, echarts.script());
    }

    /// Puts the D3 adapter in place beside a D3 library, as [#ensureAdapter(EChartsInstall)] does for ECharts.
    ///
    /// @param d3 The library.
    /// @return The adapter's Script doc.
    public DocRef ensureAdapter(final D3Install d3) {
        return ensureAdapter(ChartLibrary.D3, d3.script());
    }

    // Puts a library's adapter in place beside the library's Script doc. An adapter is never changed once
    // created, so the charts already drawn with it (on this node or another, of this Stroom version or another)
    // keep drawing as they did; a new version of the adapter is a new Script doc, named for its content. Only one
    // node at a time creates one here; two nodes creating one at once create the same content twice, which is
    // harmless, and the same one (the first by UUID) is used from then on.
    private synchronized DocRef ensureAdapter(final ChartLibrary library, final DocRef libraryScript) {
        return securityContext.asProcessingUserResult(() -> {
            final ExplorerNodeService nodeService = explorerNodeServiceProvider.get();
            final ExplorerNode folder = nodeService.getParent(libraryScript)
                    .orElseGet(nodeService::getRoot);
            final String source = adapterSource(library);
            final DocRef adapter = findAdapter(folder, source, libraryScript)
                    .orElseGet(() -> {
                        final DocRef created = createAdapter(library, folder, source, libraryScript);
                        // Another node may have created one at the same time.
                        return findAdapter(folder, source, libraryScript).orElse(created);
                    });
            documentPermissionServiceProvider.get().addDocumentPermissions(libraryScript, adapter);
            return adapter;
        });
    }

    // The adapter in a folder that holds exactly this source and depends only on the library, the first by UUID
    // if there are several. One changed by hand, or left by an earlier version of Stroom, isn't it.
    private Optional<DocRef> findAdapter(final ExplorerNode folder,
                                         final String source,
                                         final DocRef libraryScript) {
        final ScriptStore scriptStore = scriptStoreProvider.get();
        final String wanted = normalise(source);
        return NullSafe.list(explorerNodeServiceProvider.get().getChildren(folder.getDocRef())).stream()
                .filter(child -> ScriptDoc.TYPE.equals(child.getType()))
                .map(ExplorerNode::getDocRef)
                .filter(ref -> {
                    final ScriptDoc doc = read(scriptStore, ref);
                    return doc != null
                           && doc.getData() != null
                           && wanted.equals(normalise(doc.getData()))
                           && dependsOnlyOn(doc, libraryScript);
                })
                .min(Comparator.comparing(DocRef::getUuid));
    }

    private static boolean dependsOnlyOn(final ScriptDoc doc, final DocRef libraryScript) {
        final List<DocRef> dependencies = NullSafe.list(doc.getDependencies());
        return dependencies.size() == 1
               && Objects.equals(dependencies.getFirst().getUuid(), libraryScript.getUuid());
    }

    private DocRef createAdapter(final ChartLibrary library,
                                 final ExplorerNode folder,
                                 final String source,
                                 final DocRef libraryScript) {
        final String name = adapterName(library, source);
        LOGGER.info("Creating the {} adapter {} beside {} in {}", library.displayName(), name, libraryScript,
                folder.getDocRef());
        final ScriptStore scriptStore = scriptStoreProvider.get();
        final DocRef created = explorerServiceProvider.get()
                .create(ScriptDoc.TYPE, name, folder, PermissionInheritance.DESTINATION)
                .getDocRef();
        final ScriptDoc doc = scriptStore.readDocument(created);
        scriptStore.writeDocument(doc.copy()
                .description("Stroom's adapter for visualisations generated by Stroom AI, drawn with "
                             + library.displayName() + ". Created by Stroom, and never changed by it: the charts "
                             + "generated with it keep drawing with it. A changed copy isn't used for new charts.")
                .data(source)
                .dependencies(List.of(libraryScript))
                .build());
        return created;
    }

    /// Names an adapter's Script doc for its content, so each version of an adapter has its own, e.g.
    /// `ECharts Generic 3fa2c1`.
    ///
    /// @param library The library it adapts.
    /// @param source  Its source.
    /// @return Its name.
    static String adapterName(final ChartLibrary library, final String source) {
        return library.adapterName() + " " + contentHash(source);
    }

    // The first six hex digits of the SHA-256 of a source's content, as compared.
    static String contentHash(final String source) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalise(source).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 6);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// @return The bundled ECharts adapter's source.
    public String adapterSource() {
        return adapterSource(ChartLibrary.ECHARTS);
    }

    /// @param library A library.
    /// @return Its bundled adapter's source.
    public String adapterSource(final ChartLibrary library) {
        return adapterSources.computeIfAbsent(library, key -> {
            try (final InputStream in = ChartLibraries.class.getResourceAsStream(key.adapterResource())) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException("No bundled file " + key.adapterResource()));
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static Optional<Integer> adapterVersion(final String source) {
        return adapterVersion(ChartLibrary.ECHARTS, source);
    }

    /// @param library A library.
    /// @param source  A Script doc's content.
    /// @return The version in its banner, if it is the library's adapter.
    static Optional<Integer> adapterVersion(final ChartLibrary library, final String source) {
        if (source == null) {
            return Optional.empty();
        }
        final Matcher matcher = Pattern.compile(Pattern.quote(library.adapterMarker()) + "\\s*(\\d+)")
                .matcher(source);
        return matcher.find()
                ? Optional.of(Integer.parseInt(matcher.group(1)))
                : Optional.empty();
    }

    /// @param source A Script doc's content.
    /// @return The library whose adapter it is, if it is one of Stroom's adapters.
    static Optional<ChartLibrary> adapterLibrary(final String source) {
        return Arrays.stream(ChartLibrary.values())
                .filter(library -> adapterVersion(library, source).isPresent())
                .findFirst();
    }

    /// Finds the D3 library: a Script doc holding a D3 build, version 3 or later (e.g. the content pack's 3.4.8,
    /// or 7.9.0 installed by an administrator). Where several are installed, the newest version wins, so
    /// installing a newer D3 moves the charts generated from then on to it.
    ///
    /// @return The library, if one is installed.
    public Optional<D3Install> findD3Library() {
        return securityContext.asProcessingUserResult(() -> {
            final ScriptStore scriptStore = scriptStoreProvider.get();
            D3Install best = null;
            for (final DocRef ref : NullSafe.list(scriptStore.list())) {
                final ScriptDoc doc = read(scriptStore, ref);
                final Optional<String> version = doc == null
                        ? Optional.empty()
                        : d3Version(doc.getData());
                if (version.isPresent() && (best == null || compareVersions(version.get(), best.version()) > 0)) {
                    best = new D3Install(ref, version.get());
                }
            }
            return Optional.ofNullable(best);
        });
    }

    /// Says which D3 version a Script doc's content is, if it is a D3 build of version 3 or later: D3 4 and later
    /// name their version in a `d3js.org` banner; D3 3 declares it as its `version` property.
    ///
    /// @param source A Script doc's content.
    /// @return The version, e.g. `7.9.0` or `3.4.8`, if the content is such a build.
    static Optional<String> d3Version(final String source) {
        if (source == null || adapterLibrary(source).isPresent()) {
            return Optional.empty();
        }
        final Matcher banner = D3_BANNER.matcher(source);
        if (banner.find()) {
            return Optional.of(banner.group(1) + "." + banner.group(2) + "." + banner.group(3));
        }
        final Matcher v3 = D3_V3_VERSION.matcher(source);
        if ((source.contains("this.d3=") || D3_V3_OBJECT.matcher(source).find()) && v3.find()) {
            return Optional.of(v3.group(1) + "." + v3.group(2) + "." + v3.group(3));
        }
        return Optional.empty();
    }

    /// Finds the content pack's D3 Visualisations, by their UUIDs, else by their function names.
    ///
    /// @return Those installed, by chart kind.
    public Map<ChartKind, DocRef> findContentPackD3() {
        return securityContext.asProcessingUserResult(() -> {
            final VisualisationStore store = visualisationStoreProvider.get();
            final Map<ChartKind, DocRef> found = new EnumMap<>(ChartKind.class);
            final List<DocRef> all = NullSafe.list(store.list());
            for (final D3Visualisation d3 : D3Visualisation.values()) {
                all.stream()
                        .filter(ref -> d3.uuid().equals(ref.getUuid()))
                        .findFirst()
                        .ifPresent(ref -> found.put(d3.kind(), ref));
            }
            if (found.size() < D3Visualisation.values().length) {
                for (final DocRef ref : all) {
                    final VisualisationDoc doc = readVisualisation(store, ref);
                    if (doc == null) {
                        continue;
                    }
                    D3Visualisation.forFunctionName(doc.getFunctionName())
                            .ifPresent(d3 -> found.putIfAbsent(d3.kind(), ref));
                }
            }
            return found;
        });
    }

    private static VisualisationDoc readVisualisation(final VisualisationStore store, final DocRef ref) {
        try {
            return store.readDocument(ref);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// Lists every Visualisation doc, for the agent to see what is already there.
    ///
    /// @return The Visualisations the current user can see, with their function names.
    public List<InstalledVisualisation> listVisualisations() {
        final VisualisationStore store = visualisationStoreProvider.get();
        final List<InstalledVisualisation> list = new ArrayList<>();
        for (final DocRef ref : NullSafe.list(store.list())) {
            if (!securityContext.hasDocumentPermission(ref, DocumentPermission.VIEW)) {
                continue;
            }
            final VisualisationDoc doc = readVisualisation(store, ref);
            if (doc != null) {
                list.add(new InstalledVisualisation(ref, doc.getFunctionName(),
                        ChartLibrary.forFunctionName(doc.getFunctionName()).isPresent()));
            }
        }
        return list;
    }

    // ---------------------------------------------------------------------------------------------------------

    /// An installed ECharts library.
    ///
    /// @param script  Its Script doc.
    /// @param version Its version, e.g. `6.1.0`.
    public record EChartsInstall(DocRef script, String version) {

    }

    /// An installed D3 library.
    ///
    /// @param script  Its Script doc.
    /// @param version Its version, e.g. `7.9.0` or `3.4.8`.
    public record D3Install(DocRef script, String version) {

        /// @return Its major version, e.g. 7.
        public int major() {
            return Integer.parseInt(version.substring(0, version.indexOf('.')));
        }
    }

    /// A Visualisation doc in Stroom.
    ///
    /// @param docRef       The doc.
    /// @param functionName Its vis class.
    /// @param generated    True if the agent generated it (it is drawn by the ECharts adapter).
    public record InstalledVisualisation(DocRef docRef, String functionName, boolean generated) {

    }
}
