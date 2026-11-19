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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.sample.SplitterSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// The seed of a catalogue: the draft's own rules (its derivation from the fields naming kinds of record, its
/// authentication and action rules) turned into proposed event types, for the agent to correct rather than start
/// from nothing. Marked as the draft's (basis `draft`), never the user's; evidence only, nothing enters the
/// catalogue by itself.
public final class CatalogueSeed {

    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final String NETWORK = "Network";

    private CatalogueSeed() {
        // Static only.
    }

    /// @param samples  The samples, as [DraftMapping#draft] takes them.
    /// @param splitter The Data Splitter the text is read with, or null to infer it.
    /// @return The seed: the draft's rules (drafted with no source notes) as proposed event types.
    public static List<EventType> seed(final Object samples, final SplitterSpec splitter) {
        return fromDraft(DraftMapping.draft(samples, "", null, null, null, null, splitter));
    }

    /// @param draft A draft ([DraftMapping#draft]): its mapping's rules.
    /// @return One proposed event type for each rule that tells records apart (has conditions), in the draft's
    /// order: its rule's conditions the one way it is recognised, its action element, Action, outcome and who acted
    /// as the rule writes them, its TypeId the rule's own fixed Verb-Object one or the default ([TypeIds]), made
    /// unique. The rule for the rest of the records (no conditions) is the catch-all's, not an event type; but a
    /// draft of that one rule alone, writing an event (not Unknown), seeds a sole event type, recognised by every
    /// record (stroom-ai/DESIGN.md, A sole event type).
    public static List<EventType> fromDraft(final Map<String, Object> draft) {
        final Map<?, ?> mapping = draft.get("mapping") instanceof final Map<?, ?> map
                ? map
                : Map.of();
        final List<EventType> out = new ArrayList<>();
        final Set<String> typeIds = new HashSet<>();
        final List<Map<String, Object>> rules = JsonValues.maps(mapping.get("events"));
        if (rules.size() == 1 && JsonValues.maps(rules.getFirst().get("when")).isEmpty()) {
            final EventType sole = eventType(rules.getFirst(), List.of());
            if (sole.eventDetail() != null) {
                final String typeId = fixedTypeId(rules.getFirst());
                out.add(sole.withTypeId(typeId != null
                        ? typeId
                        : TypeIds.defaultTypeId(sole)));
            }
            return out;
        }
        for (final Map<String, Object> rule : rules) {
            final List<Map<String, Object>> when = JsonValues.maps(rule.get("when"));
            if (when.isEmpty()) {
                continue;
            }
            final EventType type = eventType(rule, when);
            final String typeId = unique(fixedTypeId(rule) != null
                    ? fixedTypeId(rule)
                    : TypeIds.defaultTypeId(type), typeIds);
            typeIds.add(typeId.toLowerCase(Locale.ROOT));
            out.add(type.withTypeId(typeId));
        }
        return out;
    }

    private static EventType eventType(final Map<String, Object> rule, final List<Map<String, Object>> when) {
        final List<Map<String, Object>> fields = JsonValues.maps(rule.get("fields"));
        final String detail = detailOf(fields);
        String action = null;
        String success = null;
        String actor = null;
        for (final Map<String, Object> field : fields) {
            final String path = ToolText.str(field.get("path"));
            if (detail != null && path.equals(EVENT_DETAIL + detail + "/Action") && field.get("value") != null) {
                action = ToolText.str(field.get("value"));
            } else if (detail != null && path.equals(EVENT_DETAIL + detail + "/Outcome/Success")) {
                success = field.get("value") != null
                        ? ToolText.str(field.get("value"))
                        : text(field.get("field"));
            } else if ("EventSource/User/Id".equals(path)
                       || (detail != null && path.equals(EVENT_DETAIL + detail + "/User/Id"))) {
                actor = actor == null
                        ? text(field.get("field"))
                        : actor;
            }
        }
        final List<Condition> conditions = when.stream().map(Condition::fromJson).toList();
        return new EventType(null, ToolText.str(rule.get("name")), null, detail, action, success, actor, null, null,
                conditions.isEmpty()
                        ? List.of()
                        : List.of(new Way(conditions, List.of())), false, EventType.DRAFT, "low", null,
                EventType.PROPOSED);
    }

    // The action element a rule writes (Network's with its child: Network/Permit); null for Unknown, or none.
    private static String detailOf(final List<Map<String, Object>> fields) {
        for (final Map<String, Object> field : fields) {
            final String path = ToolText.str(field.get("path"));
            if (!path.startsWith(EVENT_DETAIL) || TYPE_ID.equals(path) || "EventDetail/Description".equals(path)) {
                continue;
            }
            final String[] parts = path.split("/", -1);
            if (parts.length < 3 || "Unknown".equals(parts[1])) {
                continue;
            }
            return NETWORK.equals(parts[1]) && parts.length >= 4
                    ? parts[1] + "/" + parts[2]
                    : parts[1];
        }
        return null;
    }

    // The rule's own fixed TypeId, when it is Verb-Object; else null.
    private static String fixedTypeId(final Map<String, Object> rule) {
        return JsonValues.maps(rule.get("fields")).stream()
                .filter(field -> TYPE_ID.equals(field.get("path")) && field.get("value") != null)
                .map(field -> ToolText.str(field.get("value")))
                .filter(value -> TypeIds.problem(value) == null)
                .findFirst()
                .orElse(null);
    }

    // The TypeId, or it with the first number that makes it one no other event type has (TypeIds are compared
    // ignoring case, as titles are).
    private static String unique(final String typeId, final Set<String> taken) {
        if (!taken.contains(typeId.toLowerCase(Locale.ROOT))) {
            return typeId;
        }
        int n = 2;
        while (taken.contains((typeId + n).toLowerCase(Locale.ROOT))) {
            n++;
        }
        return typeId + n;
    }

    private static String text(final Object value) {
        return value == null || ToolText.str(value).isBlank()
                ? null
                : ToolText.str(value);
    }
}
