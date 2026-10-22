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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What the translation tools' arguments share: their descriptions, as the Stroom MCP server's
/// `tools/translation.py` and `tools/generation.py` annotate them, and how the structured ones are read.
final class TranslationArgs {

    /// `build`.
    static final String BUILD = "Build name; its workspace folder is created if needed, e.g. 'acme-door-v1.3'.";
    /// `version`.
    static final String VERSION = "The document version from the last read; the save is refused if it changed "
                                  + "since.";
    /// `uuid`.
    static final String UUID = "To change an existing document this server created: its UUID. Omit to create a "
                               + "new one in the build.";
    /// `name` of a new document.
    static final String NAME = "Document name, following the environment's naming (new documents).";
    /// `change`.
    static final String CHANGE = "What this change is and why, in a line: recorded in the XSLT's version history "
                                 + "(and the documentation's version control when it is written), e.g. 'Rule for "
                                 + "Secret Checkout events, seen once the whole feed was processed'.";
    /// `discard_hand_edit` of save_xslt.
    static final String DISCARD_HAND_EDIT = "Save over an edit made by hand in Stroom's editor since the server "
                                            + "saved the XSLT, though the new code undoes it. Only when the user has "
                                            + "said to drop their edit: a save that would undo one is refused, "
                                            + "saying what it changed, so it can be carried into the mapping or "
                                            + "plan instead.";
    /// `hand_edit_choices`.
    static final String HAND_EDIT_CHOICES = "Only from a needs_guidance reply about a hand edit, with the user's "
                                            + "answers: {'*': 'overwrite'} to overwrite the XSLT with the change "
                                            + "(every hand edit dropped), or for each field it names 'keep' (their "
                                            + "hand edit) or 'overwrite' (with the proposed field). Never chosen for "
                                            + "them.";
    /// The answers `hand_edit_choices` takes.
    static final List<String> CHOICES = List.of("keep", "overwrite", "per_field");

    private TranslationArgs() {
        // Static only.
    }

    /// @param description What the object is.
    /// @return The schema of an object argument whose keys the tool reads itself (a mapping, a plan, the answers
    /// to a question).
    static ObjectSchema object(final String description) {
        return ObjectSchema.builder().description(description).build();
    }

    /// @param call A call.
    /// @param name The argument's name.
    /// @return It as given (a map, a list, text, ...), or null if it wasn't given.
    static Object value(final ToolCall call, final String name) {
        return AbstractBuiltInTool.argsMap(call).get(name);
    }

    /// @param call A call.
    /// @param name The argument's name.
    /// @return It as a JSON object: a map as given, or text that is one; null if it wasn't given.
    /// @throws ToolRefusal if it is neither.
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(final ToolCall call, final String name) {
        final Object value = value(call, name);
        if (value == null) {
            return null;
        }
        if (value instanceof final Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof final String text && text.strip().startsWith("{")) {
            try {
                return JsonUtil.readValue(text, Map.class);
            } catch (final RuntimeException e) {
                throw new ToolRefusal(name + " is text that is not JSON: pass it as an object");
            }
        }
        throw new ToolRefusal(name + " is an object, not " + SampleArguments.pythonRepr(String.valueOf(value)));
    }

    /// @param call A call.
    /// @return The user's answers about fields edited by hand (`hand_edit_choices`), or null.
    /// @throws ToolRefusal if an answer isn't one it takes.
    static Map<String, String> handEditChoices(final ToolCall call) {
        final Map<String, Object> given = object(call, "hand_edit_choices");
        if (given == null) {
            return null;
        }
        final Map<String, String> choices = new LinkedHashMap<>();
        given.forEach((field, choice) -> {
            final String answer = NullSafe.string(NullSafe.get(choice, String::valueOf)).strip();
            if (!CHOICES.contains(answer)) {
                throw new ToolRefusal("hand_edit_choices." + field + ": Input should be 'keep', 'overwrite' or "
                                      + "'per_field'");
            }
            choices.put(field, answer);
        });
        return choices;
    }

    /// @param call A call.
    /// @return The index plan given (`index_plan`), or null.
    /// @throws ToolRefusal if it isn't one.
    static FieldPlan indexPlan(final ToolCall call) {
        final Object given = value(call, "index_plan");
        if (given == null) {
            return null;
        }
        try {
            return FieldPlan.parse(given instanceof final String text
                    ? JsonUtil.readValue(text, Map.class)
                    : given);
        } catch (final MappingValidationException e) {
            throw new ToolRefusal("index_plan is not an index plan: " + e.getMessage());
        } catch (final RuntimeException e) {
            throw new ToolRefusal("index_plan is not an index plan (draft_index_mapping drafts one): "
                                  + e.getMessage());
        }
    }
}
