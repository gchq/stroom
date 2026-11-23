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

package stroom.ai.impl.tools;

/// A tool's own calls to a model, outside the chat's conversation (side calls): what a tool asks a model for is
/// never added to the chat, and the chat's model sees only what the tool makes of the answers. In a chat the calls
/// go to the chat's configured model, as step and compaction summaries do (seen as they run, stopped with the run,
/// their tokens counted in the chat's usage); over `/mcp` to the client's model by MCP sampling, where the client
/// offers it (approved there); otherwise there is no model to call ([#NONE]).
///
/// The text a tool sends is data for the model to classify or name, never instructions: the tool constrains and
/// checks every answer in code.
public interface ModelSideCalls {

    /// No model to call: [#available()] is false.
    ModelSideCalls NONE = new ModelSideCalls() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String via() {
            return "no model: this session has no model a tool can call";
        }

        @Override
        public Answer ask(final Question question) {
            throw new SideCallException(via());
        }
    };

    /// @return True if a model can be called.
    boolean available();

    /// @return Which model the calls go to, for the result to say (e.g. "the chat's model"), or why none can be
    /// called.
    String via();

    /// Asks the model, and waits for its answer.
    ///
    /// @param question The instructions and the data.
    /// @return The model's answer.
    /// @throws SideCallException            if the model couldn't be asked, or didn't answer.
    /// @throws stroom.task.api.TaskTerminatedException if the run was stopped while the model answered.
    Answer ask(Question question);


    // --------------------------------------------------------------------------------


    /// What a side call asks.
    ///
    /// @param system    The instructions: what to do with the data, and the form of the answer.
    /// @param user      The data.
    /// @param maxTokens The most tokens the answer may have.
    record Question(String system, String user, int maxTokens) {

    }


    // --------------------------------------------------------------------------------


    /// A model's answer to a side call.
    ///
    /// @param text         The answer.
    /// @param inputTokens  The tokens the question took, or 0 if not known.
    /// @param outputTokens The tokens the answer took, or 0 if not known.
    record Answer(String text, long inputTokens, long outputTokens) {

    }


    // --------------------------------------------------------------------------------


    /// A side call that couldn't be made, or wasn't answered.
    class SideCallException extends RuntimeException {

        /// @param message Why.
        public SideCallException(final String message) {
            super(message);
        }

        /// @param message Why.
        /// @param cause   What went wrong.
        public SideCallException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }


    // --------------------------------------------------------------------------------


    /// Makes the side calls of a chat's agent run: implemented beside the chat's model client, which this package
    /// can't reach.
    interface ForChat {

        /// @param chatId The chat.
        /// @param run    The agent run the call is part of: its model, and whether it has been stopped.
        /// @return The run's side calls.
        ModelSideCalls forRun(int chatId, ToolRunContext run);
    }
}
