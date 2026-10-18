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

import stroom.openai.shared.OpenAIModelDoc;

import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// What the built-in tools may need to know about the agent run they are called in, beyond the chat: the model
/// (for tools that ask it to summarise), whether the run has been stopped, and how many times each kind of
/// query has failed (for automatic fallbacks). Held per thread for the length of the run.
public final class ToolRunContext {

    private static final ThreadLocal<ToolRunContext> CURRENT = new ThreadLocal<>();

    private final ChatModel chatModel;
    private final OpenAIModelDoc modelDoc;
    private final BooleanSupplier cancelled;
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> userActions = new ConcurrentHashMap<>();

    /// @param chatModel The model the run uses.
    /// @param modelDoc  Its document, or null.
    /// @param cancelled True once the run has been asked to stop.
    public ToolRunContext(final ChatModel chatModel, final OpenAIModelDoc modelDoc, final BooleanSupplier cancelled) {
        this.chatModel = chatModel;
        this.modelDoc = modelDoc;
        this.cancelled = cancelled;
    }

    /// Does some work with this as the current context.
    public <T> T run(final Supplier<T> work) {
        final ToolRunContext previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /// @return The context of the run this thread is working on, if any.
    public static Optional<ToolRunContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public OpenAIModelDoc getModelDoc() {
        return modelDoc;
    }

    public BooleanSupplier getCancelled() {
        return cancelled;
    }

    /// Records that a kind of query failed.
    ///
    /// @param key What failed, e.g. "table:/attachments/1.md".
    /// @return How many times it has now failed in this run.
    public int recordFailure(final String key) {
        return failures.merge(key, 1, Integer::sum);
    }

    /// Offers the user a button for a step that is theirs, on the card of the tool call's result, e.g. to create a
    /// query. It is checked (see `UserActions`) before it is shown, as an MCP tool's are.
    ///
    /// @param callId The model's id for the tool call, or null.
    /// @param action The action, as an MCP tool would give it: its `id`, `action` and arguments.
    public void offerUserAction(final String callId, final Map<String, Object> action) {
        userActions.computeIfAbsent(key(callId), k -> new CopyOnWriteArrayList<>()).add(action);
    }

    /// @param callId The model's id for a tool call, or null.
    /// @return The actions the call's tool offered, which are forgotten once taken. Empty if it offered none.
    public List<Map<String, Object>> takeUserActions(final String callId) {
        final List<Map<String, Object>> actions = userActions.remove(key(callId));
        return actions == null
                ? List.of()
                : List.copyOf(actions);
    }

    private static String key(final String callId) {
        return callId == null
                ? ""
                : callId;
    }
}
