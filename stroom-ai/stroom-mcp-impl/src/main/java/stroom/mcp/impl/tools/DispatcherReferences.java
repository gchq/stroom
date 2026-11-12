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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The reference data an aggregated feed's pipelines load at promotion: a group whose mapping looks values up needs a
/// reference loader on the dispatcher's XSLT filter, which is pipeline configuration, not a document. The registry
/// records each group's reference data; each pipeline a build promotes that runs a dispatcher (a new one, a new
/// version, or the original of a working copy, which is otherwise left as it is) loads the union of the reference
/// data of the groups its dispatcher routes, the ones it lacks added once the user approves: the one part of adding
/// a group that changes the pipeline, so said in the approval.
class DispatcherReferences {

    private final SourceTypeGroups groups;
    private final PipelineWrites writes;
    private final PipelineDocs pipelineDocs;

    /// @param groups       The feed's source type groups, read from its registry.
    /// @param writes       Makes and adds the reference entries.
    /// @param pipelineDocs Reads the pipelines.
    @Inject
    DispatcherReferences(final SourceTypeGroups groups, final PipelineWrites writes, final PipelineDocs pipelineDocs) {
        this.groups = groups;
        this.writes = writes;
        this.pipelineDocs = pipelineDocs;
    }

    /// @param build A build.
    /// @param docs  Its documents, as the plan lists them (`type`, `uuid`, `name`, `working_copy_of`).
    /// @return For each pipeline promoted that runs one of the feed's dispatchers and lacks some of its groups'
    /// reference data, what it gains; none for a build of no aggregated feed.
    List<Change> changes(final String build, final List<Map<String, Object>> docs) {
        final List<Change> out = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            if (!PipelineDoc.TYPE.equals(doc.get("type"))) {
                continue;
            }
            final String uuid = String.valueOf(doc.get("uuid"));
            final String target = doc.get("working_copy_of") == null
                    ? uuid
                    : String.valueOf(doc.get("working_copy_of"));
            final Optional<PipelineProperty> dispatcher = dispatcherProperty(uuid);
            if (dispatcher.isEmpty()) {
                continue;
            }
            final String element = dispatcher.get().getElement();
            final String dispatcherUuid = dispatcher.get().getValue().getEntity().getUuid();
            final List<ReferenceRequest> wanted = groups.routedBy(build, dispatcherUuid)
                    .map(SourceTypeGroups.Routed::references)
                    .orElse(List.of())
                    .stream()
                    .map(reference -> new ReferenceRequest(JsonValues.text(reference.get("feed")),
                            reference.get("loader_pipeline") == null
                                    ? null
                                    : JsonValues.text(reference.get("loader_pipeline")), element))
                    .toList();
            if (wanted.isEmpty()) {
                continue;
            }
            final List<PipelineReference> missing = writes.missingReferences(target, wanted);
            if (!missing.isEmpty()) {
                final String name = pipelineDocs.read(target).getName();
                out.add(new Change(new DocRef(PipelineDoc.TYPE, target, name), missing));
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

    // The pipeline's own property setting one of the feed's dispatchers as an XSLT.
    private Optional<PipelineProperty> dispatcherProperty(final String pipelineUuid) {
        return PipelineDocs.addedProperties(pipelineDocs.read(pipelineUuid)).stream()
                .filter(property -> {
                    final DocRef entity = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity);
                    return entity != null && XsltDoc.TYPE.equals(entity.getType())
                           && writes.isDispatcher(entity.getUuid());
                })
                .findFirst();
    }


    // --------------------------------------------------------------------------------


    /// Reference data a promoted pipeline gains.
    ///
    /// @param pipeline The pipeline (in production, for a working copy's original).
    /// @param added    The entries it gains.
    record Change(DocRef pipeline, List<PipelineReference> added) {

        /// @return It said for the user: the pipeline, and each reference it gains.
        String said() {
            return "pipeline '" + pipeline.getName() + "' loads reference data for its groups' lookups: "
                   + String.join(", ", added.stream().map(PipelineWrites::describe).toList());
        }
    }
}
