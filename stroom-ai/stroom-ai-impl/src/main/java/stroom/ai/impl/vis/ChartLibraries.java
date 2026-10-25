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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Finds the charting libraries installed in Stroom, and puts Stroom's ECharts adapter in place beside ECharts.
///
/// - ECharts is installed by an administrator as a Script doc holding an ECharts build, `echarts.min.js` (any name,
///   any folder). It is recognised by its content: the zrender dependency and version that each ECharts build
///   declares, version 6 or later. Any such build is used, so an administrator upgrades ECharts by putting a newer
///   build in the Script doc, with no new Stroom release. Who may edit that Script doc is a matter for its
///   permissions, as for any other visualisation script: its code runs, unsandboxed, in every generated chart.
/// - The adapter (`echarts-generic.js`, bundled here) is created on first use as a Script doc in the same folder as
///   ECharts, depending on it, with the ECharts doc's permissions, so whoever can load ECharts can load the
///   adapter. It is found again by the marker in its banner; its content and dependencies are put back to the
///   bundled ones whenever they differ, and its permissions given the library's again.
/// - D3 means the visualisations content pack's D3 Visualisations (see [D3Visualisation]).
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

    private static final Pattern ADAPTER_VERSION = Pattern.compile(Pattern.quote(ADAPTER_MARKER) + "\\s*(\\d+)");

    private final Provider<ScriptStore> scriptStoreProvider;
    private final Provider<VisualisationStore> visualisationStoreProvider;
    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<DocumentPermissionService> documentPermissionServiceProvider;
    private final SecurityContext securityContext;

    private volatile String adapterSource;

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
                        return doc != null && adapterVersion(doc.getData()).isEmpty()
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
                return doc != null && EChartsTemplate.FUNCTION_NAME.equals(doc.getFunctionName());
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Couldn't read visualisation {}: {}", visualisation,
                        e.getMessage()));
                return false;
            }
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

    /// Puts the adapter in place beside an ECharts library: finds it in the library's folder, creates it if it
    /// isn't there, and puts back the bundled content and the library as its only dependency if either differs.
    /// Either way it is given the library's permissions again, so whoever can load ECharts can load it.
    ///
    /// @param echarts The library.
    /// @return The adapter's Script doc.
    public DocRef ensureAdapter(final EChartsInstall echarts) {
        return securityContext.asProcessingUserResult(() -> {
            final ScriptStore scriptStore = scriptStoreProvider.get();
            final ExplorerNodeService nodeService = explorerNodeServiceProvider.get();
            final ExplorerNode folder = nodeService.getParent(echarts.script())
                    .orElseGet(nodeService::getRoot);
            final String source = adapterSource();

            for (final ExplorerNode child : NullSafe.list(nodeService.getChildren(folder.getDocRef()))) {
                if (!ScriptDoc.TYPE.equals(child.getType())) {
                    continue;
                }
                final ScriptDoc doc = read(scriptStore, child.getDocRef());
                if (doc == null || adapterVersion(doc.getData()).isEmpty()) {
                    continue;
                }
                final boolean sameContent = normalise(source).equals(normalise(doc.getData()));
                final List<DocRef> dependencies = NullSafe.list(doc.getDependencies());
                final boolean dependsOnLibrary = dependencies.size() == 1
                                                 && Objects.equals(dependencies.getFirst().getUuid(),
                        echarts.script().getUuid());
                if (!sameContent || !dependsOnLibrary) {
                    LOGGER.info("Putting the ECharts adapter {} back to Stroom's version {}", child.getDocRef(),
                            adapterVersion(source).orElse(0));
                    scriptStore.writeDocument(doc.copy()
                            .data(source)
                            .dependencies(List.of(echarts.script()))
                            .build());
                }
                documentPermissionServiceProvider.get().addDocumentPermissions(echarts.script(), child.getDocRef());
                return child.getDocRef();
            }

            LOGGER.info("Creating the ECharts adapter beside {} in {}", echarts.script(), folder.getDocRef());
            final DocRef created = explorerServiceProvider.get()
                    .create(ScriptDoc.TYPE, ADAPTER_NAME, folder, PermissionInheritance.DESTINATION)
                    .getDocRef();
            final ScriptDoc doc = scriptStore.readDocument(created);
            scriptStore.writeDocument(doc.copy()
                    .description("Stroom's adapter for visualisations generated by Stroom AI, drawn with ECharts. "
                                 + "Created by Stroom; changes to it are replaced when Stroom updates it.")
                    .data(source)
                    .dependencies(List.of(echarts.script()))
                    .build());
            documentPermissionServiceProvider.get().addDocumentPermissions(echarts.script(), created);
            return created;
        });
    }

    /// @return The bundled adapter's source.
    public String adapterSource() {
        String source = adapterSource;
        if (source == null) {
            try (final InputStream in = ChartLibraries.class.getResourceAsStream(ADAPTER_RESOURCE)) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException("No bundled file " + ADAPTER_RESOURCE));
                }
                source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            adapterSource = source;
        }
        return source;
    }

    static Optional<Integer> adapterVersion(final String source) {
        if (source == null) {
            return Optional.empty();
        }
        final Matcher matcher = ADAPTER_VERSION.matcher(source);
        return matcher.find()
                ? Optional.of(Integer.parseInt(matcher.group(1)))
                : Optional.empty();
    }

    /// Finds the content pack's D3 Visualisations, by their UUIDs, else by their function names.
    ///
    /// @return Those installed, by chart kind.
    public Map<ChartKind, DocRef> findD3() {
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
                        EChartsTemplate.FUNCTION_NAME.equals(doc.getFunctionName())));
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

    /// A Visualisation doc in Stroom.
    ///
    /// @param docRef       The doc.
    /// @param functionName Its vis class.
    /// @param generated    True if the agent generated it (it is drawn by the ECharts adapter).
    public record InstalledVisualisation(DocRef docRef, String functionName, boolean generated) {

    }
}
