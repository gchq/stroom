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

package stroom.ai.impl;

import stroom.ai.impl.ElicitationRecord.Status;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// Writes the forms a chat asked the user to fill in (ELICITATION messages) as markdown, for downloading the
/// chat's history: what was asked, its questions with their choices, and what became of it, with the answer if
/// the user gave one.
final class AiFormExport {

    private AiFormExport() {
        // Static only.
    }

    /// @param json An ELICITATION message (see [ElicitationRecord]).
    /// @return The form as markdown.
    static String body(final String json) {
        final ElicitationRecord form = read(json);
        if (form == null) {
            return NullSafe.string(json);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("From ").append(NullSafe.string(form.server()));
        if (NullSafe.isNonBlankString(form.tool())) {
            sb.append(" (`").append(form.tool()).append("`)");
        }
        sb.append(": ").append(status(form.status())).append("\n\n");
        if (NullSafe.isNonBlankString(form.message())) {
            sb.append(form.message().strip()).append("\n\n");
        }
        final Map<String, Object> answer = NullSafe.map(form.content());
        properties(form.schema()).forEach((name, value) -> {
            final Map<String, Object> field = asMap(value);
            sb.append("- ").append(title(name, field));
            final List<String> options = ElicitationSchema.options(field);
            if (!options.isEmpty()) {
                sb.append("\n  Choices: ").append(options.stream()
                        .map(option -> Objects.equals(option, field.get("default"))
                                ? option + " (recommended)"
                                : option)
                        .collect(Collectors.joining("; ")));
            }
            if (answer.containsKey(name)) {
                sb.append("\n  Answer: ").append(answerText(answer.get(name)));
            }
            sb.append('\n');
        });
        return sb.toString().stripTrailing();
    }

    /// @param status Where a form got to.
    /// @return What became of it, as the chat says it.
    static String status(final Status status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status) {
            case PENDING -> "waiting for an answer";
            case ACCEPTED -> "answered";
            case DECLINED -> "declined";
            case CANCELLED -> "not answered, as the run was stopped";
            case EXPIRED -> "not answered in time";
            case SUPERSEDED -> "cancelled, as a message was sent instead";
        };
    }

    private static String title(final String name, final Map<String, Object> field) {
        return field.get("title") instanceof final String title && !title.isBlank()
                ? title
                : name;
    }

    private static String answerText(final Object value) {
        if (value instanceof final List<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.joining("; "));
        }
        return String.valueOf(value);
    }

    private static Map<String, Object> properties(final Map<String, Object> schema) {
        return asMap(NullSafe.map(schema).get("properties"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return value instanceof final Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }

    private static ElicitationRecord read(final String json) {
        try {
            return JsonUtil.readValue(json, ElicitationRecord.class);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
