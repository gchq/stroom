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

/// Decides when a tool's output is too large to give the model in full, and what to give it instead: a short stub
/// saying how large the output is, where it has been saved in the chat's workspace, and how it starts. The whole
/// output stays in the workspace, so it is not lost and can be read in parts.
final class ToolOutputs {

    /// Outputs longer than this are saved to the workspace and the model is given a stub.
    static final int MAX_INLINE_CHARS = 4_000;
    /// The most lines of a large output shown in its stub.
    static final int PREVIEW_LINES = 30;
    /// The most characters of a large output shown in its stub.
    static final int PREVIEW_CHARS = 1_500;

    private ToolOutputs() {
        // Static only.
    }

    /// @param output A tool's output.
    /// @return True if it is too large to give the model in full.
    static boolean isLarge(final String output) {
        return output != null && output.length() > MAX_INLINE_CHARS;
    }

    /// @param turnId        The turn the call was made in.
    /// @param callMessageId The id of the TOOL_CALL message.
    /// @return The workspace path to save the call's output at.
    static String outputPath(final int turnId, final int callMessageId) {
        return "/outputs/turn-" + turnId + "/call-" + callMessageId + ".txt";
    }

    /// @param output The whole output.
    /// @param path   Where it has been saved in the workspace.
    /// @return What to give the model instead of the whole output.
    static String stub(final String output, final String path) {
        final String[] lines = output.split("\n", -1);
        final int lineCount = output.endsWith("\n")
                ? lines.length - 1
                : lines.length;
        final StringBuilder preview = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < lineCount && shown < PREVIEW_LINES; i++) {
            if (preview.length() + lines[i].length() > PREVIEW_CHARS) {
                if (shown == 0) {
                    preview.append(lines[i], 0, PREVIEW_CHARS).append("…");
                    shown++;
                }
                break;
            }
            preview.append(lines[i]).append('\n');
            shown++;
        }
        return "The result was too large to show in full (" + output.length() + " characters, " + lineCount
               + " lines), so it has been saved to the workspace file " + path + ". It starts with these "
               + shown + " line(s):\n\n"
               + preview.toString().stripTrailing()
               + "\n\nIf you need more of it, read it from the workspace file with the workspace tools, if you "
               + "have them.";
    }
}
