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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
/// A template may run several such XSLTs in turn before the output (one writing each line in a wrapper, the next
/// turning that into the records, say): the chain holds them all, in order, the first the one the hand-off form is
/// read from, the last the one writing the records.
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
        final List<String> after = stagesAfter(pipeline, element);
        return after.isEmpty()
                ? element
                : after.getLast();
    }

    /// The XSLTs run after the element given, in order, whose output goes to a producer, writer or appender,
    /// whoever sets them: a forwarder's shared stages, as stepping reads them.
    ///
    /// @param pipeline The pipeline as Stroom runs it.
    /// @param element  The element whose input is the Events.
    /// @return The elements and the XSLTs they run, in the order run; none for one stage.
    static List<SharedStage> sharedStages(final MergedPipeline pipeline, final String element) {
        final List<SharedStage> out = new ArrayList<>();
        final Map<String, TranslationDoc> docs = docs(pipeline, NullSafe.get(pipeline.pipeline(), DocRef::getUuid));
        for (final String id : stagesAfter(pipeline, element)) {
            final TranslationDoc doc = docs.get(id);
            final DocRef xslt = doc != null
                    ? doc.doc()
                    : xsltSet(pipeline, id);
            if (xslt != null) {
                out.add(new SharedStage(id, xslt, NullSafe.get(doc, TranslationDoc::setBy)));
            }
        }
        return out;
    }

    // The XSLT filters after the element, downstream of it, that set an XSLT and whose output goes to an output.
    private static List<String> stagesAfter(final MergedPipeline pipeline, final String element) {
        final List<String> out = new ArrayList<>();
        boolean past = false;
        for (final String id : pipeline.chain()) {
            if (id.equals(element)) {
                past = true;
                continue;
            }
            if (past && pipeline.downstream(element).contains(id) && isXsltFilter(pipeline, id)
                && setsXslt(pipeline, id) && XsltStages.writtenTo(pipeline, id).orElse(null) == Stage.OUTPUT) {
                out.add(id);
            }
        }
        return out;
    }

    // The first XSLT after the element whose output goes to an output, set by a template (or, for a template
    // itself, by any layer), and every XSLT after it before the output, whoever sets it.
    private static Optional<Chain> sharedAfter(final MergedPipeline pipeline,
                                               final String element,
                                               final String uuid,
                                               final boolean inheritedOnly,
                                               final Function<DocRef, Optional<String>> code) {
        final Map<String, TranslationDoc> docs = docs(pipeline, uuid);
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
                final List<SharedStage> stages = new ArrayList<>();
                stages.add(new SharedStage(id, doc.doc(), doc.setBy()));
                for (final String later : stagesAfter(pipeline, id)) {
                    final TranslationDoc laterDoc = docs.get(later);
                    final DocRef xslt = laterDoc != null
                            ? laterDoc.doc()
                            : xsltSet(pipeline, later);
                    if (xslt != null && XsltDoc.TYPE.equals(xslt.getType())) {
                        stages.add(new SharedStage(later, xslt, NullSafe.get(laterDoc, TranslationDoc::setBy)));
                    }
                }
                return Optional.of(new Chain(element, stages));
            }
        }
        return Optional.empty();
    }

    private static Map<String, TranslationDoc> docs(final MergedPipeline pipeline, final String uuid) {
        final Map<String, TranslationDoc> docs = new LinkedHashMap<>();
        pipeline.translationDocs(uuid).forEach(doc -> docs.put(doc.element(), doc));
        return docs;
    }

    // The XSLT an element's effective properties set, or null.
    private static DocRef xsltSet(final MergedPipeline pipeline, final String element) {
        return pipeline.properties().get(new ElementProperty(element, XSLT)) instanceof final Map<?, ?> doc
                ? new DocRef(XsltDoc.TYPE, String.valueOf(doc.get("uuid")), NullSafe.get(doc.get("name"),
                String::valueOf))
                : null;
    }

    private static boolean isXsltFilter(final MergedPipeline pipeline, final String element) {
        return XSLT_FILTER.equals(pipeline.elements().get(element));
    }

    private static boolean setsXslt(final MergedPipeline pipeline, final String element) {
        return pipeline.properties().get(new ElementProperty(element, XSLT)) != null;
    }


    // --------------------------------------------------------------------------------


    /// A chained forwarder's stages.
    ///
    /// @param flattener The element running the forwarder's own XSLT (a template's open slot).
    /// @param shared    The shared XSLTs after it, in the order run: the first reads the hand-off form, the last
    ///                  writes the records the output takes. At least one.
    record Chain(String flattener, List<SharedStage> shared) {

        /// Copies the stages.
        Chain {
            shared = List.copyOf(shared);
        }

        /// @return The element running the first shared XSLT, which reads the hand-off form.
        String stage() {
            return shared.getFirst().element();
        }

        /// @return The first shared XSLT, which reads the hand-off form.
        DocRef sharedXslt() {
            return shared.getFirst().xslt();
        }

        /// @return The pipeline (layer) that sets the first shared XSLT, or null.
        String setBy() {
            return shared.getFirst().setBy();
        }

        /// @return The shared XSLTs in words, for results: `the shared XSLT 'A'`, or `the shared XSLTs 'A' then
        /// 'B'` for several.
        String said() {
            return shared.size() == 1
                    ? "the shared XSLT '" + sharedXslt().getName() + "'"
                    : "the shared XSLTs " + shared.stream()
                            .map(stage -> "'" + stage.xslt().getName() + "'")
                            .collect(Collectors.joining(" then "));
        }

        /// @return It as results give it: `flattener`, `shared_stage` (`element`, `xslt`, `set_by`: the first), and
        /// for several, `shared_stages` (each so, in order).
        Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("flattener", flattener);
            out.put("shared_stage", shared.getFirst().toMap());
            if (shared.size() > 1) {
                out.put("shared_stages", shared.stream().map(SharedStage::toMap).toList());
            }
            return out;
        }
    }

    /// One shared XSLT of a chain.
    ///
    /// @param element The element running it.
    /// @param xslt    The XSLT.
    /// @param setBy   The pipeline (layer) that sets it, or null.
    record SharedStage(String element, DocRef xslt, String setBy) {

        /// @return It as results give it: `element`, `xslt`, `set_by`.
        Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("element", element);
            out.put("xslt", MergedPipeline.docRef(xslt));
            out.put("set_by", setBy);
            return out;
        }
    }
}
