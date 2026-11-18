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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// Which of a build's documents nothing promoted uses (decided by the user, 2026-11-18: seen in a user test, an
/// older Data Splitter the build had replaced was promoted with the rest). Promotion moves only what the build's
/// pipelines need: walked from every document that is always promoted (its pipelines and the parent pipelines they
/// inherit from, its feeds, working copies, an aggregated feed's modules, documentation of its own kind), through
/// the documents each references: a pipeline's elements' documents (XSLTs, text converters, dictionaries) and its
/// reference lookups' pipelines and feeds, an XSLT's imports, includes and dictionary lookups, a dictionary's imports.
/// What that never reaches of the build's XSLTs, text converters and dictionaries, and the documentation named after
/// only those, stays in the build. An indexing XSLT holding its index plan is always promoted (the feed's index
/// decisions and the plan's index read it, with or without a pipeline yet), as are indexes (a registry names them),
/// but an index doc whose name marks it as one to test with (`-TEST`, `-TEST2`) that nothing promoted writes to and
/// no plan or registry of the build names, and the verification dashboards searching only such indexes: test
/// documents, which stay in the build (seen in an eval, case 61: an agent's own test indexes and their dashboards
/// were promoted beside the new version's pipeline).
///
/// The walk never leaves out what it can't be sure of: in a build of no pipeline, where a pipeline of the build can't
/// be read, or one picks its XSLT or text converter by a name pattern, nothing is left out.
final class PromotionUses {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PromotionUses.class);

    /// The types of document left in the build when nothing uses them.
    static final Set<String> MAY_BE_UNUSED = Set.of(XsltDoc.TYPE, TextConverterDoc.TYPE, DictionaryDoc.TYPE);
    // A kept mapping's kind in an indexing XSLT's description: its index plan.
    private static final String INDEX_PLAN = "index";
    // An element's property choosing its document by a name pattern (an XSLT filter's xsltNamePattern, a parser's
    // namePattern): what it picks can't be told from the pipeline.
    private static final String NAME_PATTERN = "namepattern";
    // A word TEST in an index doc's name, numbered or not.
    private static final Pattern TEST_MARK = Pattern.compile("(?i)(^|[^a-z0-9])test\\d*([^a-z0-9]|$)");

    private final DocumentAccess documentAccess;

    /// @param documentAccess Reads the build's documents, as the user.
    PromotionUses(final DocumentAccess documentAccess) {
        this.documentAccess = documentAccess;
    }

    /// @param docs   The build's documents, as the build lists them.
    /// @param always Documents always promoted whatever uses them, by UUID: an aggregated feed's modules, say.
    /// @return The UUIDs of the build's documents nothing promoted uses, in the build's order; none where the walk
    /// can't be sure.
    Set<String> unused(final List<Map<String, Object>> docs, final Set<String> always) {
        final boolean pipelines = docs.stream().anyMatch(doc -> PipelineDoc.TYPE.equals(doc.get("type"))
                                                                && doc.get("working_copy_of") == null);
        if (!pipelines) {
            // No pipeline to say what is used: the build's code is promoted as it is.
            return Set.of();
        }
        final Map<String, Map<String, Object>> byUuid = new LinkedHashMap<>();
        docs.forEach(doc -> byUuid.put(String.valueOf(doc.get("uuid")), doc));
        final Set<String> reached = new HashSet<>();
        final Deque<DocRef> pending = new ArrayDeque<>();
        final Set<String> named = namedIndexes(docs);
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (!mayBeUnused(doc) || always.contains(uuid) || holdsIndexPlan(doc) || named.contains(uuid)) {
                pending.add(ref(doc));
            }
        }
        while (!pending.isEmpty()) {
            final DocRef ref = pending.poll();
            if (!reached.add(ref.getUuid())) {
                continue;
            }
            final boolean inBuild = byUuid.containsKey(ref.getUuid());
            final Optional<List<DocRef>> uses = uses(ref, inBuild, byUuid);
            if (uses.isEmpty()) {
                // Unsure: nothing is left out.
                return Set.of();
            }
            for (final DocRef used : uses.get()) {
                // Only the build's documents matter, and the parent pipelines a build's pipeline inherits from.
                if (byUuid.containsKey(used.getUuid()) || PipelineDoc.TYPE.equals(used.getType())
                                                          && PipelineDoc.TYPE.equals(ref.getType())) {
                    pending.add(used);
                }
            }
        }
        final Set<String> out = new LinkedHashSet<>();
        byUuid.forEach((uuid, doc) -> {
            if (mayBeUnused(doc) && !always.contains(uuid) && !reached.contains(uuid)) {
                out.add(uuid);
            }
        });
        out.addAll(verifyDashboardsOf(docs, out));
        out.addAll(documentationOf(docs, out));
        return out;
    }

    /// Whether an index doc's name marks it as an index to test with: a word `TEST`, numbered or not, in it (as in
    /// `ACME-INDEX-TEST`, `ACME-INDEX-TEST2`, or the tools' own `ACME-INDEX-MCP-TEST`).
    ///
    /// @param name An index doc's name.
    /// @return True if it is marked so.
    static boolean testMarked(final String name) {
        return TEST_MARK.matcher(NullSafe.string(name)).find();
    }

    // The build's index docs an index plan of the build names (by its index name) or the feed's registry names (by
    // UUID): promoted whatever writes to them, as the registry and the plan read them.
    private Set<String> namedIndexes(final List<Map<String, Object>> docs) {
        final Set<String> planned = new HashSet<>();
        final List<String> registries = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            if (XsltDoc.TYPE.equals(doc.get("type"))) {
                indexPlan(doc).map(plan -> plan.get("index_name"))
                        .filter(name -> name != null)
                        .ifPresent(name -> planned.add(String.valueOf(name).toLowerCase(Locale.ROOT)));
            } else if (DocumentationDoc.TYPE.equals(doc.get("type"))
                       && String.valueOf(doc.get("name")).endsWith(SourceTypeRegistry.SUFFIX)) {
                try {
                    documentAccess.read(ref(doc), DocumentationDoc.class)
                            .map(DocumentationDoc::getData)
                            .ifPresent(registries::add);
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> LogUtil.message("Registry {} not read: {}", doc.get("uuid"), e.getMessage()));
                }
            }
        }
        final Set<String> out = new HashSet<>();
        for (final Map<String, Object> doc : docs) {
            final String uuid = String.valueOf(doc.get("uuid"));
            if (LuceneIndexDoc.TYPE.equals(doc.get("type"))
                && (planned.contains(String.valueOf(doc.get("name")).toLowerCase(Locale.ROOT))
                    || registries.stream().anyMatch(text -> NullSafe.string(text).contains(uuid)))) {
                out.add(uuid);
            }
        }
        return out;
    }

    // The build's verification dashboards searching only indexes left in the build: test documents of those indexes,
    // left with them. A dashboard that can't be read, or searches nothing, is promoted as before.
    private Set<String> verifyDashboardsOf(final List<Map<String, Object>> docs, final Set<String> unused) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, Object> doc : docs) {
            if (!DashboardDoc.TYPE.equals(doc.get("type"))
                || !String.valueOf(doc.get("name")).endsWith(PromotionTidyUp.VERIFY)) {
                continue;
            }
            final Set<String> searched = new HashSet<>();
            try {
                documentAccess.read(ref(doc), DashboardDoc.class)
                        .map(DashboardDoc::getDashboardConfig)
                        .ifPresent(config -> {
                            for (final ComponentConfig component : NullSafe.list(config.getComponents())) {
                                if (component.getSettings() instanceof final QueryComponentSettings query
                                    && query.getDataSource() != null) {
                                    searched.add(query.getDataSource().getUuid());
                                }
                            }
                        });
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Dashboard {} not read: {}", doc.get("uuid"), e.getMessage()));
            }
            if (!searched.isEmpty() && unused.containsAll(searched)) {
                out.add(String.valueOf(doc.get("uuid")));
            }
        }
        return out;
    }

    // What a document uses, by reference; empty where it can't be told (a build's pipeline unread, a name pattern).
    // A parent pipeline outside the build that can't be read uses nothing of the build's.
    private Optional<List<DocRef>> uses(final DocRef ref,
                                        final boolean inBuild,
                                        final Map<String, Map<String, Object>> build) {
        final String type = ref.getType();
        try {
            if (PipelineDoc.TYPE.equals(type)) {
                final Optional<PipelineDoc> pipeline = documentAccess.read(ref, PipelineDoc.class);
                if (pipeline.isEmpty()) {
                    return inBuild
                            ? Optional.empty()
                            : Optional.of(List.of());
                }
                return pipelineUses(pipeline.get());
            } else if (XsltDoc.TYPE.equals(type) && inBuild) {
                return documentAccess.read(ref, XsltDoc.class)
                        .map(xslt -> named(NullSafe.string(xslt.getData()), build));
            } else if (DictionaryDoc.TYPE.equals(type) && inBuild) {
                return documentAccess.read(ref, DictionaryDoc.class)
                        .map(dictionary -> List.copyOf(NullSafe.list(dictionary.getImports())));
            }
            return Optional.of(List.of());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Couldn't read what {} uses: {}", ref, e.getMessage()));
            return inBuild
                    ? Optional.empty()
                    : Optional.of(List.of());
        }
    }

    // A pipeline's parent, its own elements' documents and its reference lookups' pipelines and feeds; empty when one
    // of its elements picks its document by a name pattern.
    private static Optional<List<DocRef>> pipelineUses(final PipelineDoc pipeline) {
        final List<DocRef> out = new ArrayList<>();
        NullSafe.consume(pipeline.getParentPipeline(), out::add);
        final PipelineData data = pipeline.getPipelineData();
        for (final PipelineProperty property : NullSafe.list(NullSafe.get(data, PipelineData::getAddedProperties))) {
            final PipelinePropertyValue value = property.getValue();
            if (NullSafe.string(property.getName()).toLowerCase(Locale.ROOT).endsWith(NAME_PATTERN)
                && NullSafe.isNonBlankString(NullSafe.get(value, PipelinePropertyValue::getString))) {
                return Optional.empty();
            }
            NullSafe.consume(NullSafe.get(value, PipelinePropertyValue::getEntity), out::add);
        }
        for (final PipelineReference reference : NullSafe.list(NullSafe.get(data,
                PipelineData::getAddedPipelineReferences))) {
            NullSafe.consume(reference.getPipeline(), out::add);
            NullSafe.consume(reference.getFeed(), out::add);
        }
        return Optional.of(out);
    }

    // The build's XSLTs and dictionaries an XSLT's code names, quoted or by UUID: its imports and includes (by name
    // or UUID, as Stroom's resolver finds them) and its dictionary lookups. Generous: a name merely quoted counts.
    private static List<DocRef> named(final String code, final Map<String, Map<String, Object>> build) {
        final List<DocRef> out = new ArrayList<>();
        build.forEach((uuid, doc) -> {
            final String type = String.valueOf(doc.get("type"));
            final String name = String.valueOf(doc.get("name"));
            if ((XsltDoc.TYPE.equals(type) || DictionaryDoc.TYPE.equals(type))
                && (code.contains(uuid) || code.contains("\"" + name + "\"") || code.contains("'" + name + "'"))) {
                out.add(ref(doc));
            }
        });
        return out;
    }

    // The build's documentation of documents left in the build: named after one (or its evaluation report), and
    // after nothing promoted of that name (a pipeline and its XSLT share one).
    private static Set<String> documentationOf(final List<Map<String, Object>> docs, final Set<String> unused) {
        final Set<String> unusedNames = new HashSet<>();
        final Set<String> usedNames = new HashSet<>();
        for (final Map<String, Object> doc : docs) {
            if (DocumentationDoc.TYPE.equals(doc.get("type"))) {
                continue;
            }
            (unused.contains(String.valueOf(doc.get("uuid")))
                    ? unusedNames
                    : usedNames).add(String.valueOf(doc.get("name")));
        }
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, Object> doc : docs) {
            final String name = String.valueOf(doc.get("name"));
            final String named = name.endsWith(WriteDocumentationTool.EVALUATION)
                    ? name.substring(0, name.length() - WriteDocumentationTool.EVALUATION.length())
                    : name;
            if (DocumentationDoc.TYPE.equals(doc.get("type")) && doc.get("working_copy_of") == null
                && unusedNames.contains(named) && !usedNames.contains(named)) {
                out.add(String.valueOf(doc.get("uuid")));
            }
        }
        return out;
    }

    // Whether a build XSLT holds an index plan (save_xslt's index_plan, kept in its description).
    private boolean holdsIndexPlan(final Map<String, Object> doc) {
        return indexPlan(doc).isPresent();
    }

    // The index plan a build XSLT holds (save_xslt's index_plan, kept in its description), if it holds one.
    private Optional<Map<String, Object>> indexPlan(final Map<String, Object> doc) {
        if (!XsltDoc.TYPE.equals(doc.get("type"))) {
            return Optional.empty();
        }
        try {
            return documentAccess.read(ref(doc), XsltDoc.class)
                    .flatMap(xslt -> KeptMapping.readMapping(xslt.getDescription()))
                    .filter(kept -> INDEX_PLAN.equals(kept.kind()))
                    .map(KeptMapping.Kept::payload);
        } catch (final RuntimeException e) {
            // Unread: walked as any other, so left out only where nothing uses it.
            LOGGER.debug(() -> LogUtil.message("No index plan read from {}: {}", doc.get("uuid"), e.getMessage()));
            return Optional.empty();
        }
    }

    // Whether a document of the build is of a kind left in it when nothing uses it: never a working copy (written
    // back over its original, or discarded). An index doc is, only when its name marks it as one to test with: an
    // index nothing writes to yet is promoted, as a registry or a later build may name it.
    private static boolean mayBeUnused(final Map<String, Object> doc) {
        final String type = String.valueOf(doc.get("type"));
        return (MAY_BE_UNUSED.contains(type)
                || LuceneIndexDoc.TYPE.equals(type) && testMarked(String.valueOf(doc.get("name"))))
               && doc.get("working_copy_of") == null;
    }

    private static DocRef ref(final Map<String, Object> doc) {
        return new DocRef(String.valueOf(doc.get("type")), String.valueOf(doc.get("uuid")),
                NullSafe.get(doc.get("name"), String::valueOf));
    }
}
