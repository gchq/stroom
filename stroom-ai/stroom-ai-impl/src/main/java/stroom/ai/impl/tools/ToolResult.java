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

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// What a [StroomTool] call gives back, as Stroom's own type rather than the chat's string, so that other callers
/// (e.g. MCP clients, later) can be given each part in their own form:
///
/// * the text, exactly as the "Ask Stroom AI" chat's model is given it;
/// * whether the call failed;
/// * optional structured content (a JSON-able map), for a tool that has an obvious structured form;
/// * the workspace files the call wrote or points to ([ToolResource]s), which the text also names by path;
/// * the steps that are the user's to take ([ToolUserAction]s), which the chat shows as buttons on the result's
///   card, and which the text describes too.
///
/// A result is immutable: the `with…` methods give a new result with a part added.
public final class ToolResult {

    private static final String ERROR_PREFIX = "Error:";

    private final String text;
    private final boolean error;
    private final Map<String, Object> structuredContent;
    private final List<ToolResource> resources;
    private final List<ToolUserAction> userActions;

    private ToolResult(final String text,
                       final boolean error,
                       final Map<String, Object> structuredContent,
                       final List<ToolResource> resources,
                       final List<ToolUserAction> userActions) {
        this.text = NullSafe.string(text);
        this.error = error;
        this.structuredContent = structuredContent;
        this.resources = resources;
        this.userActions = userActions;
    }

    /// @param text The result's text, as the chat's model is given it. Null is taken as empty.
    /// @return A result of only text. It is an error if the text starts with `Error:`, as a tool's text says when
    /// a call failed.
    public static ToolResult text(final String text) {
        return new ToolResult(text, text != null && text.startsWith(ERROR_PREFIX), Map.of(), List.of(), List.of());
    }

    /// @param text The result's text, saying what went wrong, exactly as the chat's model is given it.
    /// @return A result of a call that failed.
    public static ToolResult error(final String text) {
        return new ToolResult(text, true, Map.of(), List.of(), List.of());
    }

    /// @return The text, exactly as the chat's model is given it. Never null.
    public String text() {
        return text;
    }

    /// @return True if the call failed. It maps to MCP's `isError`.
    public boolean isError() {
        return error;
    }

    /// @return The structured content, or an empty map if there is none.
    public Map<String, Object> structuredContent() {
        return structuredContent;
    }

    /// @return The workspace files the call wrote or points to, in the order given; empty if none.
    public List<ToolResource> resources() {
        return resources;
    }

    /// @return The steps offered to the user, in the order given; empty if none.
    public List<ToolUserAction> userActions() {
        return userActions;
    }

    /// @param content Structured content (a JSON-able map), or null for none.
    /// @return This result with the structured content in place of any it had.
    public ToolResult withStructuredContent(final Map<String, Object> content) {
        final Map<String, Object> copy = content == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(content));
        return new ToolResult(text, error, copy, resources, userActions);
    }

    /// @param resource A workspace file the call wrote or points to, or null for none.
    /// @return This result with the file added to its resources.
    public ToolResult withResource(final ToolResource resource) {
        if (resource == null) {
            return this;
        }
        return new ToolResult(text, error, structuredContent, plus(resources, resource), userActions);
    }

    /// @param resources Workspace files the call wrote or points to.
    /// @return This result with the files added to its resources.
    public ToolResult withResources(final List<ToolResource> resources) {
        ToolResult result = this;
        for (final ToolResource resource : NullSafe.list(resources)) {
            result = result.withResource(resource);
        }
        return result;
    }

    /// @param action A step offered to the user.
    /// @return This result with the step added to its user actions.
    public ToolResult withUserAction(final ToolUserAction action) {
        Objects.requireNonNull(action, "action");
        return new ToolResult(text, error, structuredContent, resources, plus(userActions, action));
    }

    private static <T> List<T> plus(final List<T> list, final T item) {
        final List<T> copy = new ArrayList<>(list);
        copy.add(item);
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final ToolResult that)) {
            return false;
        }
        return error == that.error
               && text.equals(that.text)
               && structuredContent.equals(that.structuredContent)
               && resources.equals(that.resources)
               && userActions.equals(that.userActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, error, structuredContent, resources, userActions);
    }

    @Override
    public String toString() {
        return "ToolResult{error=" + error + ", resources=" + resources + ", userActions=" + userActions.size()
               + ", text=" + text + "}";
    }
}
