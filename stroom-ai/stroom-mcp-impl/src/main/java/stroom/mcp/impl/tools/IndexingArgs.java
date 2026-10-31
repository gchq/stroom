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

import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.ModelReader;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The indexing tools' models given as arguments (a field plan, planned fields, a discovery, shared templates), read as
/// leniently as [ToolArgs] reads any argument and validated, a refusal saying what is wrong.
final class IndexingArgs {

    private IndexingArgs() {
        // Static only.
    }

    /// @param call A call.
    /// @param name The plan's argument.
    /// @return The field plan given, or null.
    /// @throws ToolRefusal for one that isn't a valid plan.
    static FieldPlan plan(final ToolCall call, final String name) {
        final Object value = ToolArgs.object(call, name);
        if (value == null) {
            return null;
        }
        try {
            return FieldPlan.parse(value);
        } catch (final MappingValidationException e) {
            throw new ToolRefusal(name + " isn't a valid field plan: " + e.getMessage());
        }
    }

    /// @param call A call.
    /// @param name The discovery's argument.
    /// @return The discovery given, or null.
    /// @throws ToolRefusal for one that isn't valid.
    @SuppressWarnings("unchecked")
    static Discovery discovery(final ToolCall call, final String name) {
        final Object value = ToolArgs.object(call, name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new ToolRefusal(name + " is an object: {'input': ..., 'timestamp_field': ...}");
        }
        try {
            return Discovery.fromJson((Map<String, Object>) value);
        } catch (final MappingValidationException e) {
            throw new ToolRefusal(name + " isn't valid: " + e.getMessage());
        }
    }

    /// @param call A call.
    /// @param name The fields' argument.
    /// @return The planned fields given.
    /// @throws ToolRefusal for one that isn't valid.
    @SuppressWarnings("unchecked")
    static List<PlannedField> fields(final ToolCall call, final String name) {
        final List<PlannedField> fields = new ArrayList<>();
        for (final Object item : ToolArgs.oneOrMore(call, name)) {
            if (!(item instanceof Map<?, ?>)) {
                throw new ToolRefusal(name + " are fields, each {'name': ..., 'type': ..., 'source': ...}, not "
                                      + ToolJson.dumps(item));
            }
            try {
                fields.add(PlannedField.fromJson((Map<String, Object>) item));
            } catch (final MappingValidationException e) {
                throw new ToolRefusal(name + " isn't valid: " + e.getMessage());
            }
        }
        return fields;
    }

    /// @param call A call.
    /// @param name The shared templates' argument.
    /// @return The shared templates given.
    /// @throws ToolRefusal for one that isn't valid.
    static List<SharedTemplate> shared(final ToolCall call, final String name) {
        final List<SharedTemplate> shared = new ArrayList<>();
        for (final Object item : ToolArgs.oneOrMore(call, name)) {
            final ModelReader reader = new ModelReader();
            try {
                shared.add(reader.orThrow("SharedTemplate", SharedTemplate.read(reader, item, List.of())));
            } catch (final MappingValidationException e) {
                throw new ToolRefusal(name + " isn't valid: " + e.getMessage());
            }
        }
        return shared;
    }
}
