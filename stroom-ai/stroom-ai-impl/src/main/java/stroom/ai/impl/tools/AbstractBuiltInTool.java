/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Convenience base for {@link BuiltInTool}s that need to read their call arguments.
 */
abstract class AbstractBuiltInTool implements BuiltInTool {

    /**
     * @return the value of the named string argument, trimmed, or null if it was absent or blank.
     */
    protected String stringArg(final ToolExecutionRequest request, final String name) {
        final String json = request.arguments();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            final Map<?, ?> args = JsonUtil.readValue(json, Map.class);
            final Object value = args == null
                    ? null
                    : args.get(name);
            if (value == null) {
                return null;
            }
            final String str = String.valueOf(value).trim();
            return str.isEmpty()
                    ? null
                    : str;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * @return the value of the named string argument exactly as given (not trimmed), or null if it was absent.
     */
    protected String rawArg(final ToolExecutionRequest request, final String name) {
        final String json = request.arguments();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            final Map<?, ?> args = JsonUtil.readValue(json, Map.class);
            final Object value = args == null
                    ? null
                    : args.get(name);
            return value == null
                    ? null
                    : String.valueOf(value);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * @return the value of the named whole-number argument, or null if it was absent or not a whole number.
     */
    protected Integer intArg(final ToolExecutionRequest request, final String name) {
        final String value = stringArg(request, name);
        if (value == null) {
            return null;
        }
        try {
            final double number = Double.parseDouble(value);
            return number == Math.rint(number) && Math.abs(number) <= Integer.MAX_VALUE
                    ? (int) number
                    : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /// Reads a list of strings argument, e.g. a list of UUIDs. The model may give it as a JSON array, or as one
    /// string with the items separated by commas, and both are accepted.
    ///
    /// @param request The model's call.
    /// @param name    The argument's name.
    /// @return The items, trimmed, without empty or repeated ones. Empty if the argument is absent.
    /// @throws WorkspaceException if the arguments are not valid JSON.
    static List<String> stringListArg(final ToolExecutionRequest request, final String name) {
        final Object value = argsMap(request).get(name);
        final List<String> items = new ArrayList<>();
        if (value instanceof final List<?> list) {
            for (final Object item : list) {
                if (item != null) {
                    items.add(String.valueOf(item));
                }
            }
        } else if (value != null) {
            items.addAll(Arrays.asList(String.valueOf(value).split(",")));
        }
        return items.stream()
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    /// @param request The model's call.
    /// @return Its arguments, or an empty map if it has none.
    /// @throws WorkspaceException if they are not valid JSON.
    static Map<?, ?> argsMap(final ToolExecutionRequest request) {
        final String json = request.arguments();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            final Map<?, ?> args = JsonUtil.readValue(json, Map.class);
            return args == null
                    ? Collections.emptyMap()
                    : args;
        } catch (final RuntimeException e) {
            throw new WorkspaceException("The arguments are not valid JSON.");
        }
    }

    /// @param description What the list holds.
    /// @return The schema of a list of strings argument.
    static JsonArraySchema stringArraySchema(final String description) {
        return JsonArraySchema.builder()
                .items(JsonStringSchema.builder().build())
                .description(description)
                .build();
    }
}
