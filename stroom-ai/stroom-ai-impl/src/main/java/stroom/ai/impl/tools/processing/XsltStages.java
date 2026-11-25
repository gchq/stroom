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

package stroom.ai.impl.tools.processing;

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.processing.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.validation.EventsNamespaces;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Element;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Which stage of processing each XSLT of a pipeline is (decided with the user, 2026-11-25), by what it reads and
/// writes:
///
/// - **pre-process**: records to records, before the translation, e.g. unpacking nested JSON strings;
/// - **translation**: records to Events, where the Events namespace first appears;
/// - **decoration**: Events to Events, after the translation, e.g. adding `UserDetails/Id`;
/// - **output**: Events to something else that a later XSLT reads, e.g. a flattener writing `data` name/value
///   pairs for the XSLT that writes Kafka records (decided with the user, 2026-11-25);
/// - **indexing**: Events to documents (an index's, or another output's).
///
/// Every `XSLTFilter` of the pipeline as Stroom runs it ([MergedPipeline]: its own and its templates', in
/// processing order) is classified. Exactly, by stepping one sample record where one is given (an element whose
/// input is no Events document and whose output is one is the translation), else by each XSLT's content: the
/// namespace of what it writes ([EventsNamespaces#writtenBy]) and of what it reads (its `xpath-default-namespace`,
/// or a template matching an element of an Events namespace), helped by its place in the chain (an Events writer
/// after the translation decorates; the last XSLT of a pipeline with an indexing filter, past the Events, indexes).
/// An XSLT reading Events and writing no `Events` or `Event` ([#isOutput]) doesn't write Events, whatever namespace
/// it writes in: a flattener may keep the Events namespace for the pairs it hands on.
///
/// Read as the user: an XSLT the user can't view is classified by stepping alone, else [Stage#UNKNOWN].
public class XsltStages {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltStages.class);

    /// The longest one sample record may take to step.
    static final Duration STEP_LIMIT = Duration.ofSeconds(30);

    private static final String XSLT_FILTER = "XSLTFilter";
    private static final String XSLT_PROPERTY = "xslt";
    private static final String INDEXING_FILTER = "IndexingFilter";
    private static final Pattern QNAME_PREFIX = Pattern.compile("([A-Za-z_][\\w.\\-]*):[A-Za-z_*]");
    private static final Pattern XPATH_DEFAULT_NAMESPACE = Pattern.compile(
            "xpath-default-namespace\\s*=\\s*[\"']([^\"']*)[\"']");

    private final DocumentAccess documentAccess;
    private final HeldSchemas heldSchemas;
    private final SteppingWalks steppingWalks;

    /// @param documentAccess Reads the XSLTs as the user.
    /// @param heldSchemas    The Events namespaces: event-logging's and this Stroom's forks'.
    /// @param steppingWalks  Steps a sample record, as the user.
    @Inject
    public XsltStages(final DocumentAccess documentAccess,
                      final HeldSchemas heldSchemas,
                      final SteppingWalks steppingWalks) {
        this.documentAccess = documentAccess;
        this.heldSchemas = heldSchemas;
        this.steppingWalks = steppingWalks;
    }

    /// Classifies a pipeline's XSLTs by their content.
    ///
    /// @param pipeline The pipeline as Stroom runs it.
    /// @return Each XSLT the pipeline runs, in processing order, with its stage; empty if it runs none.
    public List<StagedXslt> classify(final MergedPipeline pipeline) {
        return StageRules.classify(steps(pipeline, null), hasIndexingFilter(pipeline));
    }

    /// Classifies a pipeline's XSLTs by stepping one record of a sample stream through it, as the user (which needs
    /// the Stepping permission); by content where stepping fails, or a step shows nothing of an element (no output:
    /// the record was dropped there).
    ///
    /// @param pipeline The pipeline as Stroom runs it.
    /// @param doc      The pipeline's document, as saved, to step.
    /// @param streamId A stream the pipeline processes, e.g. one its processor filter selects; null for none.
    /// @return Each XSLT the pipeline runs, in processing order, with its stage.
    public List<StagedXslt> classify(final MergedPipeline pipeline, final PipelineDoc doc, final Long streamId) {
        if (doc == null || streamId == null) {
            return classify(pipeline);
        }
        SteppingResult stepped = null;
        try (final SteppingWalks.Walk walk = steppingWalks.open(doc, streamId, null, STEP_LIMIT)) {
            stepped = walk.first();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Stages of pipeline {} not stepped over stream {}: {}",
                    doc.getUuid(), streamId, e.getMessage()), e);
        }
        return StageRules.classify(steps(pipeline, stepped), hasIndexingFilter(pipeline));
    }

    /// @param staged A pipeline's XSLTs, as [#classify] gave them.
    /// @param stage  A stage.
    /// @return The XSLTs of that stage, in processing order.
    public static List<StagedXslt> of(final List<StagedXslt> staged, final Stage stage) {
        return NullSafe.list(staged).stream()
                .filter(xslt -> xslt.stage() == stage)
                .toList();
    }

    /// @param staged A pipeline's XSLTs, as [#classify] gave them.
    /// @param stage  A stage.
    /// @return The first XSLT of that stage (the translation, say), or empty if there is none.
    public static Optional<StagedXslt> first(final List<StagedXslt> staged, final Stage stage) {
        return of(staged, stage).stream().findFirst();
    }

    /// Whether an XSLT is an output, by its content: it reads Events (its `xpath-default-namespace` is an Events
    /// namespace, or a template matches an element of one) and writes no `Events` or `Event` element, in any
    /// namespace (as a literal result element, an `xsl:element` of that name, or an `xsl:copy` of what it reads).
    /// A flattener is one, though it may write its pairs in the Events namespace; so is an indexing XSLT. A shared
    /// module writing a fragment of an Event (`<EventSource>`) from records reads no Events, so it isn't.
    ///
    /// @param code       An XSLT's text.
    /// @param namespaces The Events namespaces: event-logging's and this Stroom's forks'.
    /// @return True if it is an output; false if it isn't, or isn't well-formed.
    public static boolean isOutput(final String code, final Collection<String> namespaces) {
        final String text = XsltVersionHistory.strip(NullSafe.string(code));
        return XmlTrees.tryParse(text)
                .map(root -> StageRules.isOutput(root, text, namespaces))
                .orElse(false);
    }

    /// @return The Events namespaces: event-logging's, and those of this Stroom's forks of it.
    Set<String> eventsNamespaces() {
        final Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add(EventsNamespaces.STANDARD);
        try {
            heldSchemas.forks().stream()
                    .map(doc -> NullSafe.string(doc.getNamespaceURI()).strip())
                    .filter(namespace -> !namespace.isEmpty())
                    .forEach(namespaces::add);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No forks of the event-logging schema read: {}", e.getMessage()));
        }
        return namespaces;
    }

    // Each XSLTFilter of the pipeline in processing order, with what is known of what it reads and writes.
    private List<Step> steps(final MergedPipeline pipeline, final SteppingResult stepped) {
        final Set<String> namespaces = eventsNamespaces();
        final String self = NullSafe.get(pipeline.pipeline(), DocRef::getUuid);
        final Map<String, TranslationDoc> docs = new LinkedHashMap<>();
        pipeline.translationDocs(self).stream()
                .filter(doc -> XsltDoc.TYPE.equals(doc.doc().getType()))
                .forEach(doc -> docs.put(doc.element(), doc));
        final List<Step> steps = new ArrayList<>();
        for (final String element : pipeline.chain()) {
            if (!XSLT_FILTER.equals(pipeline.elements().get(element))) {
                continue;
            }
            final TranslationDoc doc = docs.get(element);
            if (doc == null && !pipeline.properties().containsKey(new ElementProperty(element, XSLT_PROPERTY))) {
                // An XSLT filter with no XSLT set: it runs nothing.
                continue;
            }
            final String code = doc == null
                    ? null
                    : documentAccess.read(doc.doc(), XsltDoc.class).map(XsltDoc::getData).orElse(null);
            final Reading content = code == null
                    ? Reading.UNREAD
                    : StageRules.byContent(code, namespaces);
            final Reading seen = stepped == null
                    ? null
                    : byStep(SteppingWalks.input(stepped, element), SteppingWalks.output(stepped, element));
            steps.add(new Step(element, NullSafe.get(doc, TranslationDoc::doc),
                    doc != null && doc.inheritedFromTemplate(), NullSafe.get(doc, TranslationDoc::setBy),
                    seen != null
                            ? seen
                            : content));
        }
        return steps;
    }

    // What one step shows an element read and wrote; null where it shows no output (nothing to tell by).
    static Reading byStep(final String input, final String output) {
        if (NullSafe.isBlankString(output)) {
            return null;
        }
        final Optional<Element> out = XmlTrees.tryParse(output);
        if (out.isEmpty()) {
            return null;
        }
        final boolean readsEvents = XmlTrees.tryParse(input).map(EventsNamespaces::isEvents).orElse(false);
        final boolean writesEvents = EventsNamespaces.isEvents(out.get());
        return new Reading(readsEvents, writesEvents, !writesEvents, How.STEPPED);
    }

    private static boolean hasIndexingFilter(final MergedPipeline pipeline) {
        return pipeline.elements().values().stream()
                .anyMatch(type -> type != null && type.endsWith(INDEXING_FILTER));
    }


    // --------------------------------------------------------------------------------


    /// The stages an XSLT may be.
    public enum Stage {
        /// Records to records, before the translation.
        PRE_PROCESS("pre-process"),
        /// Records to Events.
        TRANSLATION("translation"),
        /// Events to Events, after the translation.
        DECORATION("decoration"),
        /// Events to something else a later XSLT reads, e.g. a flattener's name/value pairs.
        OUTPUT("output"),
        /// Events to documents.
        INDEXING("indexing"),
        /// Not known: the XSLT can't be read, and wasn't stepped.
        UNKNOWN("unknown");

        private final String label;

        Stage(final String label) {
            this.label = label;
        }

        /// @return Its name in results, e.g. `pre-process`.
        public String label() {
            return label;
        }
    }


    // --------------------------------------------------------------------------------


    /// How an XSLT's stage was told.
    public enum How {
        /// By stepping a sample record: exact.
        STEPPED,
        /// By the XSLT's content and its place in the chain.
        CONTENT,
        /// Not told: the XSLT can't be read.
        UNREAD
    }


    // --------------------------------------------------------------------------------


    /// An XSLT of a pipeline, with its stage.
    ///
    /// @param element   The element that runs it.
    /// @param xslt      The XSLT, or null where the element's XSLT can't be named.
    /// @param stage     Its stage.
    /// @param inherited True if a template the pipeline inherits from sets it, not the pipeline itself.
    /// @param setBy     The name of the pipeline (layer) that sets it, or null.
    /// @param how       How its stage was told.
    public record StagedXslt(String element, DocRef xslt, Stage stage, boolean inherited, String setBy, How how) {

        /// @return It as a map: `element`, `xslt` (`type`, `uuid`, `name`), `stage`, `inherited`, `set_by` and
        /// `how` (`stepped` or `content`).
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("element", element);
            map.put("xslt", MergedPipeline.docRef(xslt));
            map.put("stage", stage.label());
            map.put("inherited", inherited);
            map.put("set_by", setBy);
            map.put("how", how.name().toLowerCase(Locale.ROOT));
            return map;
        }
    }


    // --------------------------------------------------------------------------------


    /// What an XSLT reads and writes.
    ///
    /// @param readsEvents  True if it reads Events.
    /// @param writesEvents True if it writes Events.
    /// @param writesOther  True if it writes something known not to be Events (records, JSON, ...).
    /// @param how          How this was told.
    record Reading(boolean readsEvents, boolean writesEvents, boolean writesOther, How how) {

        static final Reading UNREAD = new Reading(false, false, false, How.UNREAD);
    }


    // --------------------------------------------------------------------------------


    /// One XSLT of a pipeline, before it is classified.
    ///
    /// @param element   The element that runs it.
    /// @param xslt      The XSLT, or null.
    /// @param inherited True if a template sets it.
    /// @param setBy     The layer that sets it, or null.
    /// @param reading   What it reads and writes.
    record Step(String element, DocRef xslt, boolean inherited, String setBy, Reading reading) {

    }


    // --------------------------------------------------------------------------------


    /// The rules, apart from Stroom.
    static final class StageRules {

        private static final Set<String> EVENTS_ELEMENTS = Set.of("Events", "Event");

        private StageRules() {
            // Static only.
        }

        /// @param code       An XSLT's text.
        /// @param namespaces The Events namespaces.
        /// @return What it reads and writes, by its content.
        static Reading byContent(final String code, final Set<String> namespaces) {
            final String text = XsltVersionHistory.strip(NullSafe.string(code));
            final Optional<Element> root = XmlTrees.tryParse(text);
            if (root.isEmpty()) {
                return new Reading(false, false, false, How.CONTENT);
            }
            // An output may write in an Events namespace (a flattener's pairs) without writing Events.
            final boolean writesEvents = EventsNamespaces.writtenBy(text, namespaces).isPresent()
                                         && !isOutput(root.get(), text, namespaces);
            final boolean writesOther = !writesEvents && XmlTrees.descendantsAndSelf(root.get()).stream()
                    .anyMatch(element -> !XmlTrees.XSL.equals(element.getNamespaceURI()));
            return new Reading(readsEvents(root.get(), text, namespaces), writesEvents, writesOther, How.CONTENT);
        }

        /// @param root       An XSLT's root element.
        /// @param text       Its text.
        /// @param namespaces The Events namespaces.
        /// @return True if it reads Events and writes no `Events` or `Event` ([XsltStages#isOutput]).
        static boolean isOutput(final Element root, final String text, final Collection<String> namespaces) {
            return readsEvents(root, text, namespaces) && !writesEventsOrEvent(root);
        }

        // A literal Events or Event in any namespace, an xsl:element named one, or an xsl:copy (of what it reads,
        // Events: a decoration's identity template).
        private static boolean writesEventsOrEvent(final Element root) {
            for (final Element element : XmlTrees.descendantsAndSelf(root)) {
                if (!XmlTrees.XSL.equals(element.getNamespaceURI())) {
                    if (EVENTS_ELEMENTS.contains(element.getLocalName())) {
                        return true;
                    }
                } else if (XmlTrees.isXsl(element, "copy")) {
                    return true;
                } else if (XmlTrees.isXsl(element, "element")) {
                    final String name = element.getAttribute("name").strip();
                    final String local = name.substring(name.indexOf(':') + 1);
                    if (EVENTS_ELEMENTS.contains(local)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Its xpath-default-namespace (on any element) is an Events namespace, or a template matches an element
        // whose prefix is bound to one.
        private static boolean readsEvents(final Element root,
                                           final String text,
                                           final Collection<String> namespaces) {
            final Matcher defaults = XPATH_DEFAULT_NAMESPACE.matcher(text);
            while (defaults.find()) {
                if (namespaces.contains(defaults.group(1).strip())) {
                    return true;
                }
            }
            for (final Element element : XmlTrees.descendantsAndSelf(root)) {
                if (!XmlTrees.isXsl(element, "template") || !element.hasAttribute("match")) {
                    continue;
                }
                final Map<String, String> bound = XmlTrees.namespaces(element);
                final Matcher prefix = QNAME_PREFIX.matcher(element.getAttribute("match"));
                while (prefix.find()) {
                    if (namespaces.contains(bound.get(prefix.group(1)))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /// Classifies the XSLTs of a pipeline, in processing order.
        ///
        /// @param steps             The XSLTs, with what each reads and writes.
        /// @param hasIndexingFilter True if the pipeline writes to an index.
        /// @return Each with its stage.
        static List<StagedXslt> classify(final List<Step> steps, final boolean hasIndexingFilter) {
            final List<StagedXslt> out = new ArrayList<>();
            // The translation: the first that writes Events without reading them.
            int translation = -1;
            for (int i = 0; i < steps.size(); i++) {
                final Reading reading = steps.get(i).reading();
                if (reading.writesEvents() && !reading.readsEvents()) {
                    translation = i;
                    break;
                }
            }
            // Where the Events begin: after the translation, or from the first that reads them (an Events feed's
            // pipeline, with no translation of its own).
            int eventsFrom = translation >= 0
                    ? translation + 1
                    : -1;
            if (translation < 0) {
                for (int i = 0; i < steps.size(); i++) {
                    if (steps.get(i).reading().readsEvents()) {
                        eventsFrom = i;
                        break;
                    }
                }
            }
            // The last that doesn't write Events, past where they begin: the one that writes the documents.
            int lastNonEvents = -1;
            if (eventsFrom >= 0) {
                for (int i = steps.size() - 1; i >= eventsFrom; i--) {
                    if (!steps.get(i).reading().writesEvents() && steps.get(i).reading().how() != How.UNREAD) {
                        lastNonEvents = i;
                        break;
                    }
                }
            }
            for (int i = 0; i < steps.size(); i++) {
                final Step step = steps.get(i);
                final Reading reading = step.reading();
                final Stage stage;
                if (i == translation) {
                    stage = Stage.TRANSLATION;
                } else if (reading.how() == How.UNREAD) {
                    stage = Stage.UNKNOWN;
                } else if (eventsFrom < 0 || i < eventsFrom) {
                    // Before any Events: records to records; with no Events at all, the last XSLT of a pipeline
                    // writing to an index writes its documents (raw structured data straight to an index).
                    stage = eventsFrom < 0 && hasIndexingFilter && i == lastXslt(steps)
                            ? Stage.INDEXING
                            : Stage.PRE_PROCESS;
                } else if (reading.writesEvents()) {
                    stage = Stage.DECORATION;
                } else if (i == lastNonEvents && (reading.writesOther() || hasIndexingFilter)) {
                    stage = Stage.INDEXING;
                } else if (reading.writesOther()) {
                    // Past the Events, writing something else for a later XSLT: a flattener, say.
                    stage = Stage.OUTPUT;
                } else {
                    // Writing nothing it declares (an identity copy, say), with an XSLT after it: Events on.
                    stage = Stage.DECORATION;
                }
                out.add(new StagedXslt(step.element(), step.xslt(), stage, step.inherited(), step.setBy(),
                        reading.how()));
            }
            return out;
        }

        // The last XSLT that can be read.
        private static int lastXslt(final List<Step> steps) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                if (steps.get(i).reading().how() != How.UNREAD) {
                    return i;
                }
            }
            return -1;
        }
    }
}
