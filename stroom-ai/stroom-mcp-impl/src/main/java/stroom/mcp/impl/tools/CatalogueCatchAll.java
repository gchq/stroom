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
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.EventMeaning;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.TypeIds;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The catch-all of [CatalogueEventTypesTool]: its confirmation form for the uncatalogued records (those no event
/// type recognises), what it is shown and said as, and the translation a call gives it.
class CatalogueCatchAll {

    private static final String NAME = CatalogueEventTypesTool.NAME;

    private final RecordSourceNotesTool notes;

    /// @param notes The build's source notes: the schema the catch-all's translation is checked against.
    CatalogueCatchAll(final RecordSourceNotesTool notes) {
        this.notes = notes;
    }

    /// The catch-all's confirmation: the uncatalogued records translated as Unknown (or as the event the user said),
    /// their fields kept as Data.
    ///
    /// @param check     The catalogue's check.
    /// @param catalogue The catalogue.
    /// @param reason    Why the uncatalogued records may be Unknown, or null.
    /// @return The confirmation.
    static Consent.Request catchAllRequest(final CatalogueCheckResult check,
                                           final Catalogue catalogue,
                                           final String reason) {
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("uncatalogued records", check.uncatalogued().records() + " in the sample, of "
                                            + check.uncatalogued().shapes().size() + " shape(s), in the chat");
        final Catalogue.CatchAll.Translation own = catalogue.catchAll().translation();
        if (reason != null && own == null) {
            details.put("why", reason);
        }
        return Consent.Request.confirmation(NAME, "Keep the uncatalogued records (those no event type recognises) "
                                                  + "as " + translatedAs(own) + ", their fields kept as Data",
                details);
    }

    /// What the catch-all writes, in words: Unknown events, or the event the user said.
    ///
    /// @param own The catch-all's own translation, or null.
    /// @return What it writes, in words.
    static String translatedAs(final Catalogue.CatchAll.Translation own) {
        if (own == null || NullSafe.isBlankString(own.eventDetail())) {
            return "Unknown events";
        }
        return own.typeId() + " events (" + own.eventDetail() + (NullSafe.isBlankString(own.action())
                ? ""
                : ", action " + own.action()) + ")";
    }

    /// What the user is shown of the catch-all: the uncatalogued records' count and shapes.
    ///
    /// @param check     The catalogue's check.
    /// @param catalogue The catalogue.
    /// @return What the user is shown.
    static String catchAllShown(final CatalogueCheckResult check, final Catalogue catalogue) {
        final List<String> lines = new ArrayList<>(List.of("**Uncatalogued records**: " + check.uncatalogued().records()
                                                            + " of the sample's records no event type recognises, "
                                                            + "translated as "
                                                            + translatedAs(catalogue.catchAll().translation())
                                                            + ", their fields kept as Data."));
        final List<String> shapes = CatalogueTables.uncataloguedShapes(check);
        if (!shapes.isEmpty()) {
            lines.add("");
            shapes.forEach(shape -> lines.add("- " + shape));
        }
        return String.join("\n", lines);
    }

    /// The user declined the catch-all in its form: they want something else for the uncatalogued records, which
    /// the agent asks them in the chat (seen in a Haiku run: told only "The user did not agree to ...", the agent
    /// drafted without build= and saved a translation whose catch-all was never confirmed).
    ///
    /// @param build     The build.
    /// @param catalogue The catalogue.
    /// @param check     The catalogue's check.
    /// @return What the agent is told.
    static String catchAllDeclined(final String build,
                                   final Catalogue catalogue,
                                   final CatalogueCheckResult check) {
        return "The user declined the catch-all: they don't want the " + check.uncatalogued().records()
               + " uncatalogued records (those no event type recognises) translated as "
               + translatedAs(catalogue.catchAll().translation()) + ". Nothing is confirmed, and the "
               + "translation can't be drafted or built until it is. Ask the user in the chat what those records "
               + "should become, then call " + NAME + " build=" + build + " again with their answer: event types "
               + "that recognise them (proposals, op: add; drop: true for records they say to drop), or another "
               + "event for them all (confirm=true catch_all={translation: {type_id, event_detail, action, "
               + "description}}). The catch-all is then put to them again in its form, the only place it is "
               + "confirmed.";
    }

    /// The catalogue with the catch-all's translation the call gives (the event the user said in the chat the
    /// uncatalogued records should be): the catch-all theirs to confirm again where it changes it. Refused where the
    /// translation can't be written.
    ///
    /// @param calling The call, and what it works on.
    /// @return The catalogue, with the catch-all's translation given.
    Catalogue withCatchAllTranslation(final CatalogueEventTypesTool.Calling calling) {
        final Catalogue catalogue = calling.catalogue();
        final Map<String, Object> given = JsonValues.map(AbstractBuiltInTool.argsMap(calling.call())
                .get(CatalogueEventTypesTool.CATCH_ALL_ARG));
        final Map<String, Object> translation = given == null
                ? null
                : JsonValues.map(given.get(CatalogueEventTypesTool.TRANSLATION));
        if (translation == null) {
            return catalogue;
        }
        final Catalogue.CatchAll.Translation own = new Catalogue.CatchAll.Translation(
                givenText(translation.get("type_id")), givenText(translation.get("event_detail")),
                givenText(translation.get("action")), givenText(translation.get("description")));
        final List<String> problems = new ArrayList<>();
        final String typeIdProblem = TypeIds.problem(own.typeId());
        if (typeIdProblem != null) {
            problems.add("type_id: " + typeIdProblem);
        }
        if (NullSafe.isBlankString(own.eventDetail())) {
            problems.add("event_detail: give the action element the records are written under");
        } else {
            final EventSchema schema = notes.schemaOrNull(calling.context(), calling.build());
            if (schema != null) {
                if (!schema.hasPath("EventDetail/" + own.eventDetail())) {
                    problems.add("event_detail: EventDetail/" + own.eventDetail() + " is not an action element");
                } else {
                    problems.addAll(EventMeaning.parts(schema, "the catch-all", own.eventDetail(), null,
                            own.action()).problems());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ToolRefusal("The catch-all's translation can't be written: " + String.join("; ", problems)
                                  + ". Nothing was changed.");
        }
        if (own.equals(catalogue.catchAll().translation())) {
            return catalogue;
        }
        return catalogue.withCatchAll(new Catalogue.CatchAll(EventType.PROPOSED, catalogue.catchAll().allowUnknown(),
                own));
    }

    // A text argument as given, stripped; null when it is not given or blank.
    private static String givenText(final Object value) {
        return value instanceof final String text && !text.isBlank()
                ? text.strip()
                : null;
    }

    /// Why the uncatalogued records may be Unknown: as the call gives it, else as the catalogue holds it.
    ///
    /// @param calling The call, and what it works on.
    /// @return The reason, or null.
    String catchAllReason(final CatalogueEventTypesTool.Calling calling) {
        final Map<String, Object> given = JsonValues.map(AbstractBuiltInTool.argsMap(calling.call())
                .get(CatalogueEventTypesTool.CATCH_ALL_ARG));
        // Not ToolText.str: a catch_all without allow_unknown (its translation alone) gave the reason "None".
        final String reason = given == null
                ? null
                : givenText(given.get("allow_unknown"));
        return NullSafe.isBlankString(reason)
                ? calling.catalogue().catchAll().allowUnknown()
                : reason;
    }
}
