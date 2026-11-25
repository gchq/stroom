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

import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.processing.XsltStages;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// The two shapes of a CEF forwarder (decided with the user, 2026-11-25), told from a pipeline's elements and its
/// effective (inherited) properties, never from names:
///
/// - **chained**: the forwarder's own XSLT (a flattener) writes each CEF line in an intermediate form, and a shared
///   XSLT after it, inherited from the template, turns that into the records its output goes to (a Kafka producer,
///   a writer or an appender: [XsltStages#writtenTo]), the topic set there, once;
/// - **single**: its own XSLT writes the Kafka records (or lines of text) itself, the topic in it.
///
/// For a template, the open XSLT slot a child fills (an XSLTFilter with no XSLT set) followed by an XSLT the template
/// sets whose output goes to an output is chained. For a forwarder, its own XSLT followed by such an XSLT, inherited.
/// The shared XSLT writes no `Events` or `Event` ([XsltStages#writesEventsElements]): an Events template's
/// decoration, after its open translation slot and before its writer, is no shared stage. One the user can't read
/// can't be told, so isn't one.
final class CefChains {

    private static final String XSLT_FILTER = "XSLTFilter";
    private static final String XSLT = "xslt";

    private CefChains() {
        // Static only.
    }

    /// The chained shape of a template: the open XSLT slot a child fills, and the XSLT the template sets after it
    /// whose output goes to a producer, writer or appender.
    ///
    /// @param template The template as Stroom runs it.
    /// @param code     Reads an XSLT's text as the user; empty where it can't be read.
    /// @return The chain; empty for a template of one stage (or none).
    static Optional<Chain> ofTemplate(final MergedPipeline template, final Function<DocRef, Optional<String>> code) {
        for (final String element : template.chain()) {
            if (isXsltFilter(template, element) && !setsXslt(template, element)) {
                final Optional<Chain> found = sharedAfter(template, element, NullSafe.get(template.pipeline(),
                        DocRef::getUuid), false, code);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /// The chained shape of a forwarder: its own XSLT, and an XSLT after it, inherited from its template, whose
    /// output goes to a producer, writer or appender.
    ///
    /// @param pipeline The forwarder as Stroom runs it.
    /// @param code     Reads an XSLT's text as the user; empty where it can't be read.
    /// @return The chain; empty for one of one stage.
    static Optional<Chain> ofForwarder(final MergedPipeline pipeline, final Function<DocRef, Optional<String>> code) {
        final String uuid = NullSafe.get(pipeline.pipeline(), DocRef::getUuid);
        for (final TranslationDoc own : pipeline.translationDocs(uuid)) {
            if (!own.inheritedFromTemplate() && XsltDoc.TYPE.equals(own.doc().getType())) {
                final Optional<Chain> found = sharedAfter(pipeline, own.element(), uuid, true, code);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /// Where a pipeline's CEF lines are read: the last XSLT after the element given whose output goes to a
    /// producer, writer or appender (a shared stage writing the Kafka records), whoever sets it; else the element
    /// itself.
    ///
    /// @param pipeline The pipeline as Stroom runs it.
    /// @param element  The element whose input is the Events.
    /// @return The element whose output is the CEF lines.
    static String linesElement(final MergedPipeline pipeline, final String element) {
        String found = element;
        boolean past = false;
        for (final String id : pipeline.chain()) {
            if (id.equals(element)) {
                past = true;
                continue;
            }
            if (past && pipeline.downstream(element).contains(id) && isXsltFilter(pipeline, id)
                && setsXslt(pipeline, id) && XsltStages.writtenTo(pipeline, id).orElse(null) == Stage.OUTPUT) {
                found = id;
            }
        }
        return found;
    }

    // The first XSLT after the element whose output goes to an output, set by a template (or, for a template
    // itself, by any layer).
    private static Optional<Chain> sharedAfter(final MergedPipeline pipeline,
                                               final String element,
                                               final String uuid,
                                               final boolean inheritedOnly,
                                               final Function<DocRef, Optional<String>> code) {
        final Map<String, TranslationDoc> docs = new LinkedHashMap<>();
        pipeline.translationDocs(uuid).forEach(doc -> docs.put(doc.element(), doc));
        boolean past = false;
        for (final String id : pipeline.chain()) {
            if (id.equals(element)) {
                past = true;
                continue;
            }
            final TranslationDoc doc = docs.get(id);
            if (past && doc != null && pipeline.downstream(element).contains(id) && isXsltFilter(pipeline, id)
                && XsltDoc.TYPE.equals(doc.doc().getType()) && (!inheritedOnly || doc.inheritedFromTemplate())
                && XsltStages.writtenTo(pipeline, id).orElse(null) == Stage.OUTPUT
                && code.apply(doc.doc()).filter(text -> !XsltStages.writesEventsElements(text)).isPresent()) {
                return Optional.of(new Chain(element, id, doc.doc(), doc.setBy()));
            }
        }
        return Optional.empty();
    }

    private static boolean isXsltFilter(final MergedPipeline pipeline, final String element) {
        return XSLT_FILTER.equals(pipeline.elements().get(element));
    }

    private static boolean setsXslt(final MergedPipeline pipeline, final String element) {
        return pipeline.properties().get(new ElementProperty(element, XSLT)) != null;
    }


    // --------------------------------------------------------------------------------


    /// A chained forwarder's two stages.
    ///
    /// @param flattener  The element running the forwarder's own XSLT (a template's open slot).
    /// @param stage      The element running the shared XSLT.
    /// @param sharedXslt The shared XSLT.
    /// @param setBy      The pipeline (layer) that sets it, or null.
    record Chain(String flattener, String stage, DocRef sharedXslt, String setBy) {

        /// @return It as results give it: `flattener`, `shared_stage` (`element`, `xslt`, `set_by`).
        Map<String, Object> toMap() {
            final Map<String, Object> shared = new LinkedHashMap<>();
            shared.put("element", stage);
            shared.put("xslt", MergedPipeline.docRef(sharedXslt));
            shared.put("set_by", setBy);
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("flattener", flattener);
            out.put("shared_stage", shared);
            return out;
        }
    }
}
