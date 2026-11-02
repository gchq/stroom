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

import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.validation.OutputSchemas;
import stroom.ai.impl.tools.validation.SchemaErrors;

import java.util.List;
import java.util.Map;

/// For one walk of a pipeline's records: the output of its output element checked against the schema of its
/// namespace (see [OutputSchemas]) when no schema filter after that element checks it, each record that fails it
/// a marker of the output element. A schema filter's own errors already block a clean step; without one, the
/// element after the XSLT drops output in the wrong shape without an error, and stepping said clean.
final class UnvalidatedOutput {

    /// What a marker's message says after the schema's own error, by which [ErrorTriage] tells it.
    static final String NOT_CHECKED = "no schema filter in this pipeline checks it";

    /// Nothing checked: the pipeline validates its output itself.
    static final UnvalidatedOutput NONE = new UnvalidatedOutput(null, null);

    // Which element reads the output in each namespace, and drops what doesn't fit.
    private static final Map<String, String> READ_BY = Map.of(
            "records:2", "IndexingFilter",
            "reference-data:2", "ReferenceDataFilter",
            "kafka-records:1", "StandardKafkaProducer");

    private final OutputSchemas outputSchemas;
    private final String element;

    private UnvalidatedOutput(final OutputSchemas outputSchemas, final String element) {
        this.outputSchemas = outputSchemas;
        this.element = element;
    }

    /// @param outputSchemas Checks output against the schemas Stroom holds.
    /// @param pipeline      The pipeline walked.
    /// @return The check for its records: [#NONE] when a SchemaFilter comes after its output element (or, if that
    /// element isn't in its chain, anywhere in it).
    static UnvalidatedOutput of(final OutputSchemas outputSchemas, final SteppedPipeline pipeline) {
        final String element = pipeline.outputElement();
        final List<String> chain = pipeline.merged().chain();
        final int at = chain.indexOf(element);
        final List<String> after = at < 0
                ? chain
                : chain.subList(at + 1, chain.size());
        final boolean validated = after.stream()
                .anyMatch(id -> "SchemaFilter".equals(pipeline.types().get(id)));
        return validated
                ? NONE
                : new UnvalidatedOutput(outputSchemas, element);
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

    private ErrorTriage.Marker marker(final OutputSchemas.Failed failed, final String record) {
        final SchemaErrors.SchemaError first = failed.errors().getFirst();
        final String more = failed.errors().size() > 1
                ? " (and " + (failed.errors().size() - 1) + " more)"
                : "";
        final String message = first.message() + more + ": the output fails " + failed.systemId() + " at "
                               + first.path() + ", and " + NOT_CHECKED + "; the "
                               + READ_BY.getOrDefault(failed.namespace(), "next element")
                               + " reads only what fits the schema, and drops the rest without an error";
        final Map<String, Integer> location = first.line() > 0
                ? Map.of("line", first.line())
                : null;
        return ErrorTriage.marker("ERROR", element, message, location).withRecord(record);
    }
}
