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

import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/// What stepping needs to know about a pipeline, read once per tool call: the pipeline merged with its templates, the
/// elements it sets itself, where its output is, whether a JSON parser wraps everything in one root map, and any
/// property Stroom can't take as written.
final class SteppedPipeline {

    static final String JSON_PARSER = "JSONParser";
    static final String ADD_ROOT_OBJECT = "addRootObject";

    private final MergedPipeline merged;
    private final List<String> mistyped;

    /// @param merged   The pipeline, merged with its templates and read with its document.
    /// @param mistyped Its properties Stroom can't take as written, each in words.
    SteppedPipeline(final MergedPipeline merged, final List<String> mistyped) {
        this.merged = merged;
        this.mistyped = List.copyOf(mistyped);
    }

    /// @param merged   The pipeline, merged with its templates and read with its document.
    /// @param declared The type Stroom declares for a property, by element type and property name (`boolean`,
    ///                 `int`, `long`, `String`, a document type ...), or null where it declares none.
    /// @return It, with its properties checked against the types Stroom declares.
    static SteppedPipeline of(final MergedPipeline merged, final BiFunction<String, String, String> declared) {
        final List<String> mistyped = new ArrayList<>();
        merged.properties().forEach((key, value) -> {
            final String kind = declared.apply(merged.elements().get(key.element()), key.name());
            if (value == null) {
                return;
            }
            final boolean wrong = ("boolean".equals(kind) && !(value instanceof Boolean))
                                  || (("int".equals(kind) || "long".equals(kind))
                                      && !(value instanceof Integer || value instanceof Long));
            if (wrong) {
                mistyped.add(key.element() + "." + key.name() + " is " + ToolText.repr(value) + ", where Stroom takes "
                             + ("boolean".equals(kind)
                        ? "true or false"
                        : "a whole number"));
            }
        });
        return new SteppedPipeline(merged, mistyped);
    }

    /// Seen: jsonParser.addRootObject written as the string 'false', and every step, of any stream, outlasted
    /// Stroom's wait; with several Stroom nodes that read as a load-balancing failure.
    ///
    /// @throws ToolRefusal if the pipeline has a property Stroom can't take as written.
    void refuseMistyped() {
        if (!mistyped.isEmpty()) {
            throw new ToolRefusal("Pipeline '" + name() + "' has a property Stroom can't take as written, and it "
                                  + "steps nothing until it is fixed (each step outlasts Stroom's wait): "
                                  + String.join("; ", mistyped) + ". Set it again with update_pipeline "
                                  + "(set_properties), which writes the value as Stroom's type.");
        }
    }

    /// @return The pipeline, merged with its templates.
    MergedPipeline merged() {
        return merged;
    }

    /// @return The pipeline's document, as saved.
    PipelineDoc doc() {
        return merged.doc();
    }

    /// @return The pipeline's name.
    String name() {
        return merged.doc().getName();
    }

    /// @return The pipeline, as a document reference.
    DocRef ref() {
        return new DocRef(PipelineDoc.TYPE, merged.doc().getUuid(), merged.doc().getName());
    }

    /// @return Each element's type by its id.
    Map<String, String> types() {
        return merged.elements();
    }

    /// @return The elements the pipeline itself adds or configures.
    Set<String> own() {
        return merged.ownElements();
    }

    /// @return The pipeline's reference loaders, with those it inherits.
    List<PipelineReference> references() {
        return merged.references();
    }

    /// @return The pipeline's own XSLT steps, which is where its translation happens; with no XSLT at all (a
    /// pipeline writing its parser's records as Records), the parser, whose output is what is written.
    /// @throws ToolRefusal if it has neither, so nothing it writes can be looked at.
    List<String> defaultOutputs() {
        final List<String> outputs = merged.defaultOutputs();
        if (outputs.isEmpty()) {
            throw new ToolRefusal("Pipeline '" + name() + "' has no XSLT step and no parser, so there is no "
                                  + "output of it to look at");
        }
        return outputs;
    }

    /// @return The element whose output is what the pipeline writes: the last of [#defaultOutputs()].
    String outputElement() {
        return defaultOutputs().getLast();
    }

    /// @return True if a JSON parser wraps everything in one root map, which makes a JSON array a single record.
    boolean jsonRootMap() {
        return merged.elements().entrySet().stream()
                .anyMatch(entry -> JSON_PARSER.equals(entry.getValue()) && !Boolean.FALSE.equals(addRootObject(
                        entry.getKey())));
    }

    // The value of a JSON parser's addRootObject property, or null if it isn't set.
    private Object addRootObject(final String element) {
        return merged.properties().get(new ElementProperty(element, ADD_ROOT_OBJECT));
    }

    /// @return The JSON parser's element id, or null.
    String jsonParser() {
        return merged.elements().entrySet().stream()
                .filter(entry -> JSON_PARSER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
