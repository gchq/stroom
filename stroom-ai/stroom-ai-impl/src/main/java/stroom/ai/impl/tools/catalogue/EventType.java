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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.ModelReader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// One event type of a source's catalogue: what type of event it is, what it means, how a record of it is
/// recognised, and where that knowledge came from.
///
/// @param typeId         `type_id`: Verb-Object (`Update-Password`), unique in the build; null until given (see
///                       [TypeIds#defaultTypeId(EventType)]).
/// @param title          A short title (`Password change`), unique in the build.
/// @param description    What happened, as a reader is told it.
/// @param eventDetail    `event_detail`: the event-logging action element (`Update`, `Network/Permit`).
/// @param action         Its Action, or null.
/// @param success        Its outcome (a field, or a fixed value), or null.
/// @param actor          The field naming who acted, or null.
/// @param affected       The field naming what was acted on, or null.
/// @param affectedAs     `affected_as`: what that is under the action element (`User`), or null.
/// @param recognisedBy   `recognised_by`: the ways a record of it is told; empty only for a sole event type.
/// @param drop           True when its records are dropped (the user's word only).
/// @param basis          `draft`, `agent_knowledge`, `documentation` or `user_confirmed`.
/// @param confidence     `high`, `medium` or `low`.
/// @param oneTypeBecause `one_type_because`: why records that look like several event types are one, or null.
/// @param status         `proposed` or `confirmed`.
/// @param doubt          Optional: why the agent is unsure of it (a medium or low confidence's reason), said to the user
///                       where the event type is worth a look; null when none was given.
public record EventType(String typeId,
                        String title,
                        String description,
                        String eventDetail,
                        String action,
                        String success,
                        String actor,
                        String affected,
                        String affectedAs,
                        List<Way> recognisedBy,
                        boolean drop,
                        String basis,
                        String confidence,
                        String oneTypeBecause,
                        String status,
                        String doubt) {

    /// The seed's, from the draft's rules: never the user's until the user confirms it.
    public static final String DRAFT = "draft";
    /// From the agent's own knowledge of the product.
    public static final String AGENT_KNOWLEDGE = "agent_knowledge";
    /// From the vendor's documentation.
    public static final String DOCUMENTATION = "documentation";
    /// The user's: confirmed (or amended, or dropped) in the user's form.
    public static final String USER_CONFIRMED = "user_confirmed";
    /// The bases an event type may have.
    public static final List<String> BASES = List.of(DRAFT, AGENT_KNOWLEDGE, DOCUMENTATION, USER_CONFIRMED);
    /// The confidences an event type may have.
    public static final List<String> CONFIDENCES = List.of("high", "medium", "low");
    /// Proposed, not yet confirmed.
    public static final String PROPOSED = "proposed";
    /// Confirmed by the user.
    public static final String CONFIRMED = "confirmed";
    /// The statuses an event type (and the catch-all) may have.
    public static final List<String> STATUSES = List.of(PROPOSED, CONFIRMED);

    /// Fills in an empty list for a null one, and null for a blank doubt.
    public EventType {
        recognisedBy = List.copyOf(Objects.requireNonNullElse(recognisedBy, List.of()));
        doubt = doubt == null || doubt.isBlank()
                ? null
                : doubt.strip();
    }

    /// An event type with no doubt given.
    ///
    /// @param typeId         Its TypeId, or null.
    /// @param title          Its title.
    /// @param description    What happened.
    /// @param eventDetail    Its action element.
    /// @param action         Its Action, or null.
    /// @param success        Its outcome, or null.
    /// @param actor          Who acted, or null.
    /// @param affected       What was acted on, or null.
    /// @param affectedAs     What that is under the action element, or null.
    /// @param recognisedBy   The ways a record of it is told.
    /// @param drop           True when its records are dropped.
    /// @param basis          Where it came from.
    /// @param confidence     `high`, `medium` or `low`.
    /// @param oneTypeBecause Why records that look like several event types are one, or null.
    /// @param status         `proposed` or `confirmed`.
    public EventType(final String typeId,
                     final String title,
                     final String description,
                     final String eventDetail,
                     final String action,
                     final String success,
                     final String actor,
                     final String affected,
                     final String affectedAs,
                     final List<Way> recognisedBy,
                     final boolean drop,
                     final String basis,
                     final String confidence,
                     final String oneTypeBecause,
                     final String status) {
        this(typeId, title, description, eventDetail, action, success, actor, affected, affectedAs, recognisedBy,
                drop, basis, confidence, oneTypeBecause, status, null);
    }

    /// Reads one from JSON-like values, laxly (strings, booleans as words, defaults for what isn't given), every
    /// error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't read.
    public static EventType read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "EventType");
        if (fields == null) {
            return null;
        }
        final EventType eventType = new EventType(fields.optionalString("type_id"),
                fields.optionalString("title"),
                fields.optionalString("description"),
                fields.optionalString("event_detail"),
                fields.optionalString("action"),
                textOrNull(fields, "success"),
                fields.optionalString("actor"),
                fields.optionalString("affected"),
                fields.optionalString("affected_as"),
                fields.models("recognised_by", (value, at) -> Way.read(reader, value, at)),
                fields.bool("drop", false),
                fields.literal("basis", AGENT_KNOWLEDGE, BASES),
                fields.literal("confidence", "medium", CONFIDENCES),
                fields.optionalString("one_type_because"),
                fields.literal("status", PROPOSED, STATUSES),
                fields.optionalString("doubt"));
        return fields.ok()
                ? eventType
                : null;
    }

    // An outcome may be given as a boolean (true, false) or a field's name: read as its text.
    private static String textOrNull(final ModelReader.Fields fields, final String key) {
        final Object given = fields.input().get(key);
        return given instanceof final Boolean bool
                ? String.valueOf(bool)
                : fields.optionalString(key);
    }

    /// @return It as JSON, in the catalogue's key order: every key, null ones too, but `doubt`, which is optional and
    /// given only when the agent gave one.
    public Map<String, Object> toJson() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("type_id", typeId);
        out.put("title", title);
        out.put("description", description);
        out.put("event_detail", eventDetail);
        out.put("action", action);
        out.put("success", success);
        out.put("actor", actor);
        out.put("affected", affected);
        out.put("affected_as", affectedAs);
        out.put("recognised_by", recognisedBy.stream().map(Way::toJson).toList());
        out.put("drop", drop);
        out.put("basis", basis);
        out.put("confidence", confidence);
        out.put("one_type_because", oneTypeBecause);
        out.put("status", status);
        if (doubt != null) {
            out.put("doubt", doubt);
        }
        return out;
    }

    /// @return It confirmed by the user, in the user's form: the seed's (basis draft) becomes the user's; one from
    /// documentation or the agent's knowledge keeps its basis.
    public EventType confirmedByUser() {
        return new EventType(typeId, title, description, eventDetail, action, success, actor, affected, affectedAs,
                recognisedBy, drop, DRAFT.equals(basis)
                ? USER_CONFIRMED
                : basis, confidence, oneTypeBecause, CONFIRMED, doubt);
    }

    /// @return It dropped by the user, in the user's form: its records left untranslated, on the user's word.
    public EventType droppedByUser() {
        return new EventType(typeId, title, description, eventDetail, action, success, actor, affected, affectedAs,
                recognisedBy, true, USER_CONFIRMED, confidence, oneTypeBecause, CONFIRMED, doubt);
    }

    /// @return It confirmed.
    public boolean confirmed() {
        return CONFIRMED.equals(status);
    }

    /// @param typeId Its new TypeId.
    /// @return It with that TypeId.
    public EventType withTypeId(final String typeId) {
        return new EventType(typeId, title, description, eventDetail, action, success, actor, affected, affectedAs,
                recognisedBy, drop, basis, confidence, oneTypeBecause, status, doubt);
    }
}
