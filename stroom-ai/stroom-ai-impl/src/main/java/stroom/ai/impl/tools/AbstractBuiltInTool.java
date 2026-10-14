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

import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

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
}
