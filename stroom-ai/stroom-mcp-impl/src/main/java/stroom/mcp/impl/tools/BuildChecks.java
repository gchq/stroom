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
import stroom.ai.impl.tools.sample.PyJson;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What a build's pipelines still lack before promotion (the Stroom MCP server's `tools/builds.py`
/// `build_checks`): a clean step of their current code (recorded in the build's record), a Documentation doc (for
/// new pipelines), an Elasticsearch index template agreed and the sample indexed and verified (for indexing
/// pipelines), a Field mapping section that matches the current mapping and XSLT, and an XSLT that is what its
/// mapping generates ([GeneratedDrift]).
class BuildChecks {

    /// Said of a check promotion waits for, rather than one shown in the approval: documentation left behind by the
    /// build's own change to a mapping or XSLT (asked for by the user, after an agent promoted with it stale and then
    /// made a second doc trying to fix it). Not when the XSLT was changed by hand: that stays a warning, so a user's
    /// own edits never hold a promotion up.
    static final String BLOCKS = " (promotion waits for this)";

    private static final Pattern DOC_MARK = Pattern.compile("<!-- stroom-mcp field-mapping ([0-9a-f]{16}) -->");

    private final BuildRecords buildRecords;
    private final PipelineShapes pipelineShapes;
    private final IndexTemplateAgreements agreements;
    private final KeptMappings keptMappings;
    private final DocumentAccess documentAccess;
    private final GeneratedDrift mappingDrift;

    @Inject
    BuildChecks(final BuildRecords buildRecords,
                final PipelineShapes pipelineShapes,
                final IndexTemplateAgreements agreements,
                final KeptMappings keptMappings,
                final DocumentAccess documentAccess,
                final GeneratedDrift mappingDrift) {
        this.buildRecords = buildRecords;
        this.pipelineShapes = pipelineShapes;
        this.agreements = agreements;
        this.keptMappings = keptMappings;
        this.documentAccess = documentAccess;
        this.mappingDrift = mappingDrift;
    }

    /// @param docs A build's documents, as the plan lists them (`type`, `uuid`, `name`, `working_copy_of`).
    /// @return What its pipelines still lack, each as a line naming the pipeline.
    List<String> checks(final List<Map<String, Object>> docs) {
        final Map<String, Map<String, Object>> documented = new LinkedHashMap<>();
        docs.stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.get("type")))
                .forEach(doc -> documented.put(String.valueOf(doc.get("name")), doc));
        final List<String> problems = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            if (PipelineDoc.TYPE.equals(doc.get("type"))) {
                check(doc, documented, problems);
            }
        }
        return problems;
    }

    private void check(final Map<String, Object> doc,
                       final Map<String, Map<String, Object>> documented,
                       final List<String> problems) {
        final String name = String.valueOf(doc.get("name"));
        final String uuid = String.valueOf(doc.get("uuid"));
        final DocRef ref = new DocRef(PipelineDoc.TYPE, uuid, name);
        final String said = "Pipeline '" + name + "': ";
        if (!steppedClean(ref)) {
            problems.add(said + "no clean step_sample or step_records of its current code is recorded");
        }
        if (doc.get("working_copy_of") == null && !documented.containsKey(name)) {
            problems.add(said + "no documentation (write_documentation)");
        }
        try {
            final String stage = pipelineShapes.shape(uuid).stage();
            if ("indexing".equals(stage) || "discovery".equals(stage)) {
                final Optional<Map<String, Object>> destination = agreements.elasticDestination(uuid);
                if (destination.isPresent() && agreements.pipeline(uuid)
                        .flatMap(pipeline -> agreements.agreementProblem(pipeline, destination.get()))
                        .isPresent()) {
                    problems.add(said + "no Elasticsearch index template agreed with the user for its current code "
                                 + "(propose_index_template with their example)");
                }
                if (!buildRecords.verified(ref)) {
                    problems.add(said + "the sample has not been indexed and verified with its current code "
                                 + "(create_processor_filter, wait_for_processing, verify_index)");
                }
            }
            final Optional<KeptMappings.KeptXslt> kept = keptMappings.keptXslt(uuid);
            if (kept.isEmpty()) {
                mappingDrift.lostMappings(uuid).forEach(lost -> problems.add(said + lost));
                return;
            }
            final Optional<String> drift = "translation".equals(kept.get().kept().kind())
                    ? mappingDrift.drift(kept.get())
                    : mappingDrift.planDrift(kept.get());
            drift.ifPresent(why -> problems.add(said + why));
            if (documented.containsKey(name)) {
                final String body = documentAccess.read(new DocRef(DocumentationDoc.TYPE,
                                String.valueOf(documented.get(name).get("uuid"))), DocumentationDoc.class)
                        .map(BuildRecords::bodyText)
                        .orElse("");
                if (!mappingDigest(kept.get()).equals(docDigest(body))) {
                    // The build's own change (the XSLT is what its mapping generates) blocks; a hand edit only warns.
                    final boolean blocks = "translation".equals(kept.get().kept().kind()) && drift.isEmpty();
                    problems.add(said + "the documentation's Field mapping predates the current mapping or XSLT "
                                 + "(write_documentation build=<this build> pipeline_uuid='" + uuid + "' "
                                 + "stream_ids=<its sample streams> change=<what changed>)" + (blocks
                            ? BLOCKS
                            : ""));
                }
            }
        } catch (final PipelineUnreadableException e) {
            // A pipeline Stroom can't load (a working copy of a production one with a property its element type
            // lost): one problem said, the build's other checks still made.
            problems.add(said + "Stroom can't load it: " + e.getMessage());
        }
    }

    private boolean steppedClean(final DocRef ref) {
        try {
            return buildRecords.steppedClean(ref);
        } catch (final PipelineUnreadableException e) {
            // Said below, as Stroom can't load it.
            return false;
        }
    }

    /// @param kept A mapping kept with its XSLT.
    /// @return The digest of the mapping and the XSLT the documentation's Field mapping is generated from.
    static String mappingDigest(final KeptMappings.KeptXslt kept) {
        return XsltCode.digest(PyJson.dumpsSortedKeys(kept.kept().payload()),
                XsltCode.normalise(kept.xslt().getData()));
    }

    /// @param markdown A Documentation doc's text.
    /// @return The digest its Field mapping section was generated from, or null if it has none.
    static String docDigest(final String markdown) {
        final Matcher matcher = DOC_MARK.matcher(NullSafe.string(markdown));
        return matcher.find()
                ? matcher.group(1)
                : null;
    }
}
