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

package stroom.gwt.workbench.framework.client.preview;

import stroom.gwt.workbench.framework.client.play.PlayRunner.LogEntry;
import stroom.gwt.workbench.framework.client.play.PlayRunner.RunStatus;
import stroom.gwt.workbench.framework.client.play.PlayRunner.Status;
import stroom.gwt.workbench.framework.client.story.Story;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.Collection;
import java.util.List;

/// Builds the JSON that the workbench exposes to the test runner (`stroom-gwt-workbench/test-runner`),
/// so that it can find and check the stories without scraping the page. It is plain Java so that it
/// can be tested on the JVM; [RunnerHooks] puts the JSON on the page.
///
/// * [#index(StoryRegistry)] - every story, in the same shape as the entries of a Storybook
///   `index.json` (v5), with an extra `hasPlay` flag. It is exposed as `window.__workbenchIndex`.
/// * [#playState] - the progress of the story's play function in the preview, exposed as
///   `window.__workbenchPlay`.
public final class RunnerJson {

    /// The format version of the index, the same as Storybook's `index.json`.
    public static final int INDEX_VERSION = 5;
    /// The status of the preview before the story has rendered.
    public static final String RENDERING_STATUS = "RENDERING";

    private RunnerJson() {
        // Static utility
    }

    /// Builds an index of all the stories, in the order they are shown in the sidebar, e.g.
    /// ```
    /// {"v":5,"entries":{"widgets-buttons-button--default":{"type":"story",
    ///   "id":"widgets-buttons-button--default","title":"Widgets/Buttons/Button","name":"Default",
    ///   "tags":[],"hasPlay":false}}}
    /// ```
    ///
    /// @param registry All the stories.
    /// @return The index as JSON.
    public static String index(final StoryRegistry registry) {
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":").append(INDEX_VERSION).append(",\"entries\":{");
        boolean first = true;
        for (final Story story : registry.getStories()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(story.getId())).append(":{");
            sb.append("\"type\":\"story\"");
            field(sb, "id", story.getId());
            field(sb, "title", story.getTitle());
            field(sb, "name", story.getName());
            sb.append(",\"tags\":");
            array(sb, story.getTags());
            sb.append(",\"hasPlay\":").append(story.getPlay() != null);
            if (story.getSourceClassName() != null) {
                field(sb, "sourceClass", story.getSourceClassName());
            }
            sb.append('}');
        }
        sb.append("}}");
        return sb.toString();
    }

    /// Builds the state of a story's play function, e.g.
    /// ```
    /// {"storyId":"widgets-buttons-button--primary","status":"ERRORED","hasPlay":true,
    ///   "nextStep":1,"stepCount":3,"failedStep":"expect(...).toBeVisible()",
    ///   "error":"...","entries":[...]}
    /// ```
    ///
    /// @param storyId   The id of the story, or null if there isn't one.
    /// @param status    The status, i.e. [#RENDERING_STATUS] or the name of a [RunStatus].
    /// @param hasPlay   Whether the story has a play function.
    /// @param entries   Every step of the play function, including nested steps, in order.
    /// @param nextStep  The index of the next top level step to run.
    /// @param stepCount The number of top level steps.
    /// @param error     An error rendering the story, or null. If null and a step has failed, the
    ///                  error is the step's.
    /// @return The state as JSON.
    public static String playState(final String storyId,
                                   final String status,
                                   final boolean hasPlay,
                                   final List<LogEntry> entries,
                                   final int nextStep,
                                   final int stepCount,
                                   final String error) {
        // The deepest failed step is the one that actually failed, rather than a group containing it
        LogEntry failed = null;
        for (final LogEntry entry : entries) {
            if (entry.getStatus() == Status.ERROR
                && (failed == null || entry.getDepth() > failed.getDepth())) {
                failed = entry;
            }
        }
        final String stepError = failed != null
                ? failed.getError()
                : null;

        final StringBuilder sb = new StringBuilder();
        sb.append("{\"storyId\":").append(quote(storyId));
        field(sb, "status", status);
        sb.append(",\"hasPlay\":").append(hasPlay);
        sb.append(",\"nextStep\":").append(nextStep);
        sb.append(",\"stepCount\":").append(stepCount);
        field(sb, "failedStep", failed != null
                ? failed.getText()
                : null);
        field(sb, "error", error != null
                ? error
                : stepError);
        sb.append(",\"entries\":[");
        for (int i = 0; i < entries.size(); i++) {
            final LogEntry entry = entries.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"depth\":").append(entry.getDepth());
            field(sb, "text", entry.getText());
            field(sb, "status", entry.getStatus().name());
            if (entry.getError() != null) {
                field(sb, "error", entry.getError());
            }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    /// @param value A string, or null.
    /// @return The string as a JSON string literal, or `null`.
    static String quote(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // Other control characters, and the line/paragraph separators that aren't
                    // allowed unescaped in JavaScript before ES2019
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        final String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static void field(final StringBuilder sb, final String name, final String value) {
        sb.append(',').append(quote(name)).append(':').append(quote(value));
    }

    private static void array(final StringBuilder sb, final Collection<String> values) {
        sb.append('[');
        boolean first = true;
        for (final String value : values) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(value));
        }
        sb.append(']');
    }
}
