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

import stroom.ai.impl.tools.processing.ErrorTriage;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.validation.OutputSchemas;
import stroom.ai.impl.tools.validation.SchemaErrors;
import stroom.pipeline.shared.stepping.SteppingResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// For one walk of a pipeline's records: the output of its output element checked against the schema of its
/// namespace (see [OutputSchemas]) when no schema filter after that element checks it, each record that fails it
/// a marker of the output element. A schema filter's own errors already block a clean step; without one, the
/// element after the XSLT drops output in the wrong shape without an error, and stepping said clean.
///
/// Likewise a translation's Events (the first XSLT writing Events in an event-logging namespace, or a fork's)
/// where no schema filter follows it: checked against the event-logging schema (or the fork's) as stepped, each
/// record that fails it a marker of the translation, and the check said in the result ([#eventsSaid]). A
/// hand-written pipeline may have no schema filter, and its invalid Events are then written as they are.
final class UnvalidatedOutput {

    /// What a marker's message says after the schema's own error, by which [ErrorTriage] tells it.
    static final String NOT_CHECKED = "no schema filter in this pipeline checks it";
    /// What an Events marker's message says, by which [ErrorTriage] tells it.
    static final String EVENTS_NOT_CHECKED = "no schema filter after the translation checks its Events";

    /// Nothing checked: the pipeline validates its output itself.
    static final UnvalidatedOutput NONE = new UnvalidatedOutput(null, null, List.of(), Set.of());

    // Which element reads the output in each namespace, and drops what doesn't fit.
    private static final Map<String, String> READ_BY = Map.of(
            "records:2", "IndexingFilter",
            "reference-data:2", "ReferenceDataFilter",
            "kafka-records:1", "StandardKafkaProducer");
    private static final String SCHEMA_FILTER = "SchemaFilter";
    private static final String XSLT_FILTER = "XSLTFilter";

    private final OutputSchemas outputSchemas;
    private final String element;
    // The pipeline's XSLT elements in chain order, and those a schema filter follows.
    private final List<String> xslts;
    private final Set<String> validated;
    // What the walk's Events check met: the translation element, the schemas used, records checked and failed.
    private String eventsElement;
    private final Set<String> eventsSchemas = new TreeSet<>();
    private int eventsChecked;
    private int eventsFailed;

    private UnvalidatedOutput(final OutputSchemas outputSchemas,
                              final String element,
                              final List<String> xslts,
                              final Set<String> validated) {
        this.outputSchemas = outputSchemas;
        this.element = element;
        this.xslts = xslts;
        this.validated = validated;
    }

    /// @param outputSchemas Checks output against the schemas Stroom holds.
    /// @param pipeline      The pipeline walked.
    /// @return The check for its records: [#NONE] when a SchemaFilter comes after its output element (or, if that
    /// element isn't in its chain, anywhere in it), which then follows every XSLT before it too.
    static UnvalidatedOutput of(final OutputSchemas outputSchemas, final SteppedPipeline pipeline) {
        final String element = pipeline.outputElement();
        final List<String> chain = pipeline.merged().chain();
        final Map<String, String> types = pipeline.types();
        if (validatedAfter(pipeline, chain, element)) {
            return NONE;
        }
        final List<String> xslts = chain.stream()
                .filter(id -> XSLT_FILTER.equals(types.get(id)))
                .toList();
        final Set<String> validated = new HashSet<>();
        xslts.stream()
                .filter(id -> validatedAfter(pipeline, chain, id))
                .forEach(validated::add);
        return new UnvalidatedOutput(outputSchemas, element, xslts, validated);
    }

    // True where a schema filter reads the element's output, following its links (anywhere in the pipeline, for
    // an element not in its chain). A schema filter left unlinked checks nothing.
    private static boolean validatedAfter(final SteppedPipeline pipeline,
                                          final List<String> chain,
                                          final String element) {
        final Map<String, String> types = pipeline.types();
        final Collection<String> after = chain.contains(element)
                ? pipeline.merged().downstream(element)
                : chain;
        return after.stream().anyMatch(id -> SCHEMA_FILTER.equals(types.get(id)));
    }

    /// @param output What the output element wrote for the record.
    /// @param record The record's key.
    /// @return A marker of the first error the schema reports, with how many it reports; none if the output is
    /// valid, or not checked.
    List<ErrorTriage.Marker> markers(final String output, final String record) {
        if (outputSchemas == null) {
            return List.of();
        }
        return outputSchemas.check(output)
                .map(failed -> List.of(marker(failed, record)))
                .orElse(List.of());
    }

    /// The translation's Events for one record, checked where no schema filter follows the translation: the first
    /// XSLT element in the chain whose output is Events in an event-logging namespace (or a fork's).
    ///
    /// @param result The record's step.
    /// @param record The record's key.
    /// @return A marker of the first error the schema reports, with how many it reports; none if the Events are
    /// valid, a schema filter checks them, or the record wrote none.
    List<ErrorTriage.Marker> eventMarkers(final SteppingResult result, final String record) {
        if (outputSchemas == null) {
            return List.of();
        }
        for (final String xslt : xslts) {
            final Optional<OutputSchemas.Checked> checked = outputSchemas.checkEvents(SteppingWalks.output(result,
                    xslt));
            if (checked.isPresent()) {
                // The first writing Events is the translation: checked here only where nothing after it checks.
                return validated.contains(xslt)
                        ? List.of()
                        : counted(xslt, checked.get(), record);
            }
        }
        return List.of();
    }

    /// @return What the walk's Events check did, for the result: the translation, the schema, and how many records
    /// failed it; empty where nothing was checked (a schema filter follows the translation, or no Events).
    Optional<String> eventsSaid() {
        if (eventsElement == null || eventsChecked == 0) {
            return Optional.empty();
        }
        return Optional.of("No schema filter follows " + eventsElement + ", so stepping checked its Events against "
                           + String.join(", ", eventsSchemas) + ": " + (eventsFailed == 0
                ? "all " + eventsChecked + " record(s) valid"
                : eventsFailed + " of " + eventsChecked + " record(s) failed it, each an error of " + eventsElement)
                           + ". Processing writes Events as they are, valid or not, with no schema filter.");
    }

    private List<ErrorTriage.Marker> counted(final String xslt,
                                             final OutputSchemas.Checked checked,
                                             final String record) {
        eventsElement = xslt;
        eventsSchemas.add(checked.systemId());
        eventsChecked++;
        if (checked.errors().isEmpty()) {
            return List.of();
        }
        eventsFailed++;
        final SchemaErrors.SchemaError first = checked.errors().getFirst();
        final String message = first.message() + more(checked.errors()) + ": the Events fail " + checked.systemId()
                               + " at " + first.path() + ", and " + EVENTS_NOT_CHECKED + "; processing writes "
                               + "them as they are, invalid";
        return List.of(ErrorTriage.marker("ERROR", xslt, message, location(first)).withRecord(record));
    }

    private ErrorTriage.Marker marker(final OutputSchemas.Failed failed, final String record) {
        final SchemaErrors.SchemaError first = failed.errors().getFirst();
        final String message = first.message() + more(failed.errors()) + ": the output fails " + failed.systemId()
                               + " at " + first.path() + ", and " + NOT_CHECKED + "; the "
                               + READ_BY.getOrDefault(failed.namespace(), "next element")
                               + " reads only what fits the schema, and drops the rest without an error";
        return ErrorTriage.marker("ERROR", element, message, location(first)).withRecord(record);
    }

    private static String more(final List<SchemaErrors.SchemaError> errors) {
        return errors.size() > 1
                ? " (and " + (errors.size() - 1) + " more)"
                : "";
    }

    private static Map<String, Integer> location(final SchemaErrors.SchemaError first) {
        return first.line() > 0
                ? Map.of("line", first.line())
                : null;
    }
}
