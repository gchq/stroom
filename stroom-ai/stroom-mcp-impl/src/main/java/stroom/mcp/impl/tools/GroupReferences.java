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

import stroom.ai.impl.tools.JsonValues;
import stroom.docref.DocRef;
import stroom.mcp.impl.tools.PipelineWrites.ReferenceRequest;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The reference data an aggregated feed's group pipelines load at promotion: a group whose mapping looks values up
/// needs a reference loader on its pipeline's XSLT filter, which is pipeline configuration, not a document. The
/// registry records each group's reference data (its rows' `references`); each pipeline running a group's XSLT
/// loads that group's, the ones it lacks added once the user approves, so said in the review and the approval. The
/// pipelines are the build's (a new one, a new version, or the original of a working copy, which is otherwise left
/// as it is) and the production pipelines running a group the build changes through a working copy of its XSLT: a
/// later build changing a group holds only the XSLT's working copy, not the pipeline.
class GroupReferences {

    private final SourceTypeGroups groups;
    private final PipelineWrites writes;
    private final PipelineDocs pipelineDocs;
    private final Provider<PipelineIndex> pipelineIndex;

    /// @param groups        The feed's source type groups, read from its registry.
    /// @param writes        Makes and adds the reference entries.
    /// @param pipelineDocs  Reads the pipelines.
    /// @param pipelineIndex Finds the production pipelines running a group's XSLT.
    @Inject
    GroupReferences(final SourceTypeGroups groups,
                    final PipelineWrites writes,
                    final PipelineDocs pipelineDocs,
                    final Provider<PipelineIndex> pipelineIndex) {
        this.groups = groups;
        this.writes = writes;
        this.pipelineDocs = pipelineDocs;
        this.pipelineIndex = pipelineIndex;
    }

    /// @param build A build.
    /// @param docs  Its documents, as the plan lists them (`type`, `uuid`, `name`, `working_copy_of`).
    /// @return For each pipeline promoted, or in production running a group the build changes through a working
    /// copy, that runs a group's XSLT and lacks some of the group's reference data, what it gains; none for a build
    /// of no aggregated feed.
    List<Change> changes(final String build, final List<Map<String, Object>> docs) {
        final List<Change> out = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final Map<String, Object> doc : docs) {
            if (!PipelineDoc.TYPE.equals(doc.get("type"))) {
                continue;
            }
            final String uuid = String.valueOf(doc.get("uuid"));
            final String target = doc.get("working_copy_of") == null
                    ? uuid
                    : String.valueOf(doc.get("working_copy_of"));
            seen.add(uuid);
            seen.add(target);
            for (final PipelineProperty property : xsltProperties(uuid)) {
                final String xslt = property.getValue().getEntity().getUuid();
                final Optional<SourceTypeGroups.GroupRun> group = groups.groupOf(build, xslt);
                if (group.isPresent()) {
                    change(target, property.getElement(), group.get()).ifPresent(out::add);
                    break;
                }
            }
        }
        // Production pipelines running a group the build changes through a working copy of its XSLT.
        for (final Map<String, Object> doc : docs) {
            if (!XsltDoc.TYPE.equals(doc.get("type")) || doc.get("working_copy_of") == null) {
                continue;
            }
            final String copy = String.valueOf(doc.get("uuid"));
            final String original = String.valueOf(doc.get("working_copy_of"));
            final Optional<SourceTypeGroups.GroupRun> group = groups.groupOf(build, copy);
            if (group.isEmpty()) {
                continue;
            }
            for (final DocRef pipeline : pipelineIndex.get().containing(PipelineDoc.TYPE, original)) {
                if (!seen.add(pipeline.getUuid())) {
                    continue;
                }
                xsltProperties(pipeline.getUuid()).stream()
                        .filter(property -> Set.of(original, copy).contains(property.getValue().getEntity()
                                .getUuid()))
                        .findFirst()
                        .flatMap(property -> change(pipeline.getUuid(), property.getElement(), group.get()))
                        .ifPresent(out::add);
            }
        }
        return out;
    }

    /// Adds each change's reference data to its pipeline.
    ///
    /// @param changes What [#changes] found, approved.
    /// @return What was done.
    List<String> apply(final List<Change> changes) {
        final List<String> done = new ArrayList<>();
        for (final Change change : changes) {
            writes.addReferences(change.pipeline().getUuid(), change.added());
            done.add(change.said());
        }
        return done;
    }

    // What a pipeline running a group's XSLT on an element gains: the group's reference data it lacks.
    private Optional<Change> change(final String target,
                                    final String element,
                                    final SourceTypeGroups.GroupRun group) {
        final List<ReferenceRequest> wanted = group.references().stream()
                .map(reference -> new ReferenceRequest(JsonValues.text(reference.get("feed")),
                        reference.get("loader_pipeline") == null
                                ? null
                                : JsonValues.text(reference.get("loader_pipeline")), element))
                .toList();
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        final List<PipelineReference> missing = writes.missingReferences(target, wanted);
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        final String name = pipelineDocs.read(target).getName();
        return Optional.of(new Change(new DocRef(PipelineDoc.TYPE, target, name), group.group(), missing));
    }

    // The pipeline's own properties setting an XSLT; none for a pipeline that can't be read.
    private List<PipelineProperty> xsltProperties(final String pipelineUuid) {
        try {
            return PipelineDocs.addedProperties(pipelineDocs.read(pipelineUuid)).stream()
                    .filter(property -> {
                        final DocRef entity = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
                        return entity != null && XsltDoc.TYPE.equals(entity.getType());
                    })
                    .toList();
        } catch (final RuntimeException e) {
            // A pipeline that can't be read is left as it is.
            return List.of();
        }
    }


    // --------------------------------------------------------------------------------


    /// Reference data a promoted pipeline gains.
    ///
    /// @param pipeline The pipeline (in production, for a working copy's original).
    /// @param group    The source type group it runs.
    /// @param added    The entries it gains.
    record Change(DocRef pipeline, String group, List<PipelineReference> added) {

        /// @return It said for the user: the pipeline, and each reference it gains.
        String said() {
            return "pipeline '" + pipeline.getName() + "' loads reference data for group '" + group + "''s lookups: "
                   + String.join(", ", added.stream().map(PipelineWrites::describe).toList());
        }
    }
}
