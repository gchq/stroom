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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.validation.EventSchema.Child;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What an event-logging element takes, from the schema in this Stroom: its children in order, each with whether
/// it is required or repeatable, whether it is one of a choice (and whether one of that choice must be given), and
/// for a leaf its type and allowed values, each with the schema's description. Ported from the Stroom MCP server's
/// `describe_event_element` (`describe_document`'s element=). Seen: Qwen in VS Code spent twelve minutes writing
/// PowerShell to read these out of the XSD.
public final class EventElements {

    /// The element described when none is named: EventDetail, whose children are the action elements.
    public static final String DEFAULT_ELEMENT = "EventDetail";

    private EventElements() {
        // Static only.
    }

    /// @param heldSchemas The schemas this Stroom holds.
    /// @param path        A path below Event, e.g. `EventDetail/Authenticate` ("" for Event itself).
    /// @param version     The event-logging version, or null for `chosen`.
    /// @param chosen      The version the user chose for the build, or null for the newest this Stroom holds.
    /// @return What the element takes, as [#describe(EventSchema, String, String)] gives it.
    /// @throws ValidationException if Stroom holds no such schema, or it has no such element.
    public static Map<String, Object> describe(final HeldSchemas heldSchemas,
                                               final String path,
                                               final String version,
                                               final String chosen) {
        final String used = version != null && !version.isBlank()
                ? version
                : heldSchemas.chosenOrLatest(chosen);
        return describe(heldSchemas.eventSchema(used), path, used);
    }

    /// @param schema  The event-logging schema.
    /// @param path    A path below Event, e.g. `EventDetail/Authenticate` ("" for Event itself).
    /// @param version The schema's version, said in the result.
    /// @return `path` (from Event), `schema_version`, `description`; for a leaf `type` and `values`; otherwise
    /// `children` (each `name`, and as they apply `required`, `repeatable`, `choice`, `type`, `values` and
    /// `description`) and `choices` (each `choice`, `one_of` and `one_required`).
    /// @throws ValidationException if the schema has no such element, saying what is allowed where it stops.
    public static Map<String, Object> describe(final EventSchema schema, final String path, final String version) {
        final List<Child> chain;
        try {
            chain = schema.resolve(path == null
                    ? ""
                    : path);
        } catch (final IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        final Element decl = chain.isEmpty()
                ? schema.event()
                : chain.getLast().decl();
        final List<String> names = new ArrayList<>();
        names.add("Event");
        chain.forEach(child -> names.add(child.name()));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", String.join("/", names));
        result.put("schema_version", version);
        if (!chain.isEmpty() && !schema.describe(chain).isEmpty()) {
            result.put("description", schema.describe(chain));
        }
        if (schema.isLeaf(decl)) {
            result.put("type", schema.baseType(decl));
            if (schema.enumeration(decl) != null) {
                result.put("values", schema.enumeration(decl));
            }
            return result;
        }
        final List<Map<String, Object>> children = new ArrayList<>();
        final Map<Integer, List<String>> choices = new LinkedHashMap<>();
        for (final Child child : schema.children(decl)) {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", child.name());
            if (child.required()) {
                item.put("required", true);
            }
            if (child.repeatable()) {
                item.put("repeatable", true);
            }
            if (child.choice() != null) {
                item.put("choice", child.choice());
                choices.computeIfAbsent(child.choice(), k -> new ArrayList<>()).add(child.name());
            }
            if (schema.isLeaf(child.decl())) {
                item.put("type", schema.baseType(child.decl()));
                if (schema.enumeration(child.decl()) != null) {
                    item.put("values", schema.enumeration(child.decl()));
                }
            }
            final List<Child> below = new ArrayList<>(chain);
            below.add(child);
            final String described = schema.describe(below);
            if (!described.isEmpty()) {
                item.put("description", described);
            }
            children.add(item);
        }
        result.put("children", children);
        if (!choices.isEmpty()) {
            final List<Map<String, Object>> listed = new ArrayList<>();
            choices.forEach((choice, oneOf) -> {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("choice", choice);
                entry.put("one_of", oneOf);
                entry.put("one_required", schema.requiredChoices().containsKey(choice));
                listed.add(entry);
            });
            result.put("choices", listed);
        }
        return result;
    }
}
