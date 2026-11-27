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

package stroom.ai.impl.tools.sequence;

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The window (`maxspan`) a `sequence_query` call ([SequenceQueryTool]) asks the user for when the model doesn't
/// give it: the form's choices, the user's answer read as a window, and what the call is told where no form can
/// be shown.
final class SequenceWindow {

    private static final List<Duration> OTHER_WINDOWS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(15),
            Duration.ofHours(1));

    private SequenceWindow() {
        // Static methods only.
    }

    /// @param suggested The configured window.
    /// @return The form's choices, the suggested one first (marked the default), then a few others, then another
    /// interval, each with its window (null for another interval).
    static Map<String, Duration> maxspanOptions(final Duration suggested) {
        final Map<String, Duration> options = new LinkedHashMap<>();
        options.put(windowLabel(suggested) + " (the default)", suggested);
        for (final Duration other : OTHER_WINDOWS) {
            if (!other.equals(suggested)) {
                options.put(windowLabel(other), other);
            }
        }
        options.put(SequenceQueryTool.ANOTHER_INTERVAL, null);
        return options;
    }

    /// @param options The choices.
    /// @return The form: one text answer, the choices as its suggestions, the first its default.
    static Map<String, Object> maxspanForm(final Map<String, Duration> options) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "string");
        field.put("title", SequenceQueryTool.MAXSPAN_QUESTION);
        field.put("examples", List.copyOf(options.keySet()));
        field.put("default", options.keySet().iterator().next());
        field.put("maxLength", 60);
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", Map.of(SequenceQueryTool.MAXSPAN_FORM, field));
        form.put("required", List.of(SequenceQueryTool.MAXSPAN_FORM));
        return form;
    }

    /// @param chosen  The user's answer: a choice, or an interval in their own words.
    /// @param options The choices.
    /// @return The window.
    static Duration chosenMaxspan(final String chosen, final Map<String, Duration> options) {
        if (NullSafe.isBlankString(chosen) || SequenceQueryTool.ANOTHER_INTERVAL.equals(chosen)) {
            throw new WorkspaceException("The user chose another interval but didn't give it, so nothing was run. "
                                         + "Ask them in the chat how far apart the steps may be, then call again "
                                         + "with maxspan.");
        }
        final Duration option = options.get(chosen);
        if (option != null) {
            return option;
        }
        try {
            return EqlQueryWriter.maxspan(chosen.replaceAll("(?i)\\s*\\(the default\\)$", ""));
        } catch (final WorkspaceException e) {
            throw new WorkspaceException("The user's window '" + chosen + "' can't be read, so nothing was run. Ask "
                                         + "them in the chat how far apart the steps may be (e.g. 10m), then call "
                                         + "again with maxspan.");
        }
    }

    /// @param suggested The configured window.
    /// @return What a call without a window is told where the user can't be asked in a form.
    static String maxspanRefusal(final Duration suggested) {
        return "Give maxspan: how far apart the first and last events may be, at most 24h. Ask the user: '"
               + SequenceQueryTool.MAXSPAN_QUESTION + "' Suggest " + EqlQueryWriter.maxspanText(suggested)
               + " (the default), or " + String.join(", ", OTHER_WINDOWS.stream()
                .filter(other -> !other.equals(suggested))
                .map(EqlQueryWriter::maxspanText)
                .toList()) + ", or another interval. " + SequenceQueryTool.WIDER_NOTE
               + " Then call again with maxspan= their answer.";
    }

    private static String windowLabel(final Duration duration) {
        final long ms = duration.toMillis();
        if (ms % 3_600_000 == 0) {
            return plural(ms / 3_600_000, "hour");
        } else if (ms % 60_000 == 0) {
            return plural(ms / 60_000, "minute");
        } else if (ms % 1_000 == 0) {
            return plural(ms / 1_000, "second");
        }
        return plural(ms, "millisecond");
    }

    private static String plural(final long n, final String unit) {
        return n + " " + unit + (n == 1
                ? ""
                : "s");
    }
}
