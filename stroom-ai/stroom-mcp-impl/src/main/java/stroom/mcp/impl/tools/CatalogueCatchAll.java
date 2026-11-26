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
import java.util.Optional;

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
        return catchAllRequest(check, catalogue, reason, null);
    }

    /// As [#catchAllRequest(CatalogueCheckResult, Catalogue, String)], for a catalogue read back from an existing
    /// pipeline that writes no Event for the uncatalogued records: they are left so, as it leaves them.
    ///
    /// @param check          The catalogue's check.
    /// @param catalogue      The catalogue.
    /// @param reason         Why the uncatalogued records may be Unknown, or null.
    /// @param untranslatedBy The existing pipeline that leaves them untranslated, or null for Unknown (or the event
    ///                       the user said).
    /// @return The confirmation.
    static Consent.Request catchAllRequest(final CatalogueCheckResult check,
                                           final Catalogue catalogue,
                                           final String reason,
                                           final String untranslatedBy) {
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("uncatalogued records", check.uncatalogued().records() + " in the sample, of "
                                            + check.uncatalogued().shapes().size() + " shape(s), in the chat");
        final Catalogue.CatchAll.Translation own = catalogue.catchAll().translation();
        final CatalogueCheckResult.Uncatalogued uncatalogued = check.uncatalogued();
        if (uncatalogued.dropped() > 0 || uncatalogued.mayBeDropped() > 0) {
            details.put(untranslatedBy != null && own == null
                    ? "left untranslated"
                    : "kept as " + translatedAs(own), uncatalogued.unknown() + " of the uncatalogued records");
        }
        dropped(check, catalogue).ifPresent(said -> details.put("records dropped", said));
        if (uncatalogued.unparsed() > 0) {
            details.put("held values that don't parse", uncatalogued.unparsed() + " of the uncatalogued records "
                                                        + "(held JSON or XML), written as the catch-all writes them");
        }
        if (!uncatalogued.mayBe().isEmpty()) {
            details.put("not checked here", String.join("; ", uncatalogued.mayBe().stream()
                    .map(CatalogueCheckResult.MayBe::said)
                    .toList()));
        }
        if (untranslatedBy != null && own == null) {
            return Consent.Request.confirmation(NAME, "Leave the uncatalogued records (those no event type "
                                                      + "recognises) untranslated (no Event), as '" + untranslatedBy
                                                      + "' leaves them", details);
        }
        if (reason != null && own == null) {
            details.put("why", reason);
        }
        return Consent.Request.confirmation(NAME, "Keep the uncatalogued records (those no event type recognises) "
                                                  + "as " + translatedAs(own) + ", their fields kept as Data",
                details);
    }

    /// The records drop rules drop, in words: those the catalogue's drop rules recognise, and those of the
    /// uncatalogued records the build's mapping's own drop rules drop (seen in testing: every record of the sample
    /// said to become Unknown, whatever the drops planned).
    ///
    /// @param check     The catalogue's check.
    /// @param catalogue The catalogue.
    /// @return E.g. `4,380 by the catalogue's drop rules; 20 of the uncatalogued records by the build's mapping's
    /// drop rules`; empty when none are dropped.
    static Optional<String> dropped(final CatalogueCheckResult check, final Catalogue catalogue) {
        final int byCatalogue = droppedByDropRules(check, catalogue);
        final int byMapping = check.uncatalogued().dropped();
        final List<String> parts = new ArrayList<>();
        if (byCatalogue > 0) {
            parts.add(String.format("%,d", byCatalogue) + " by the catalogue's drop rules");
        }
        if (byMapping > 0) {
            parts.add(String.format("%,d", byMapping) + " of the uncatalogued records by the build's mapping's drop "
                      + "rules");
        }
        if (check.uncatalogued().mayBeDropped() > 0) {
            parts.add(String.format("%,d", check.uncatalogued().mayBeDropped()) + " more may be ("
                      + check.uncatalogued().mayBeDroppedBecause() + ")");
        }
        return parts.isEmpty()
                ? Optional.empty()
                : Optional.of(String.join("; ", parts));
    }

    // The sample's records the catalogue's drop rules recognise.
    private static int droppedByDropRules(final CatalogueCheckResult check, final Catalogue catalogue) {
        int out = 0;
        for (final EventType type : catalogue.eventTypes()) {
            final CatalogueCheckResult.Count count = type.drop()
                    ? check.counts().get(type.typeId())
                    : null;
            if (count != null) {
                out += count.records();
            }
        }
        return out;
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
        return catchAllShown(check, catalogue, null);
    }

    /// As [#catchAllShown(CatalogueCheckResult, Catalogue)], for a catalogue read back from an existing pipeline
    /// that writes no Event for the uncatalogued records.
    ///
    /// @param check          The catalogue's check.
    /// @param catalogue      The catalogue.
    /// @param untranslatedBy The existing pipeline that leaves them untranslated, or null.
    /// @return What the user is shown.
    static String catchAllShown(final CatalogueCheckResult check,
                                final Catalogue catalogue,
                                final String untranslatedBy) {
        final String become = untranslatedBy != null && catalogue.catchAll().translation() == null
                ? "left untranslated (no Event), as '" + untranslatedBy + "' leaves them"
                : "translated as " + translatedAs(catalogue.catchAll().translation()) + ", their fields kept as "
                  + "Data";
        final CatalogueCheckResult.Uncatalogued uncatalogued = check.uncatalogued();
        final List<String> dropParts = new ArrayList<>();
        if (uncatalogued.dropped() > 0) {
            dropParts.add(uncatalogued.dropped() + " dropped by the build's mapping's drop rules");
        }
        if (uncatalogued.mayBeDropped() > 0) {
            dropParts.add(uncatalogued.mayBeDropped() + " may be dropped by them ("
                          + uncatalogued.mayBeDroppedBecause() + ")");
        }
        final List<String> lines = new ArrayList<>(List.of("**Uncatalogued records**: " + uncatalogued.records()
                                                            + " of the sample's records no event type recognises"
                                                            + (dropParts.isEmpty()
                ? ", " + become + "."
                : ": " + uncatalogued.unknown() + " " + become + "; " + String.join("; ", dropParts) + ".")
                                                            + (uncatalogued.unparsed() > 0
                ? " " + uncatalogued.unparsed() + " of them have held JSON or XML that doesn't parse."
                : "")));
        if (!uncatalogued.mayBe().isEmpty()) {
            // Neither recognised nor not: said so, never counted as 0 or as Unknown (found in review, 2026-11-27).
            lines.add("");
            lines.add("Not checked here: " + String.join("; ", uncatalogued.mayBe().stream()
                    .map(CatalogueCheckResult.MayBe::said)
                    .toList()) + ".");
        }
        final List<String> shapes = CatalogueTables.uncataloguedShapes(check);
        if (!shapes.isEmpty()) {
            lines.add("");
            shapes.forEach(shape -> lines.add("- " + shape));
        }
        final int byCatalogue = droppedByDropRules(check, catalogue);
        if (byCatalogue > 0) {
            lines.add("");
            lines.add("**" + CatalogueSummary.heading(CatalogueSummary.DROPPED) + "**: " + byCatalogue + " more of the "
                      + "sample's records, recognised by the catalogue's drop rules.");
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
        return catchAllDeclined(build, catalogue, check, null, null);
    }

    /// As [#catchAllDeclined(String, Catalogue, CatalogueCheckResult)], for a catalogue read back from an existing
    /// pipeline that writes no Event for the uncatalogued records.
    ///
    /// @param build          The build.
    /// @param catalogue      The catalogue.
    /// @param check          The catalogue's check.
    /// @param untranslatedBy The existing pipeline that leaves them untranslated, or null.
    /// @param notRecorded    Why the latest proposals, sent over the catalogue as it is, recorded nothing, or null
    ///                       where they were recorded.
    /// @return What the agent is told.
    static String catchAllDeclined(final String build,
                                   final Catalogue catalogue,
                                   final CatalogueCheckResult check,
                                   final String untranslatedBy,
                                   final String notRecorded) {
        final String become = (untranslatedBy != null && catalogue.catchAll().translation() == null
                ? "left untranslated (no Event), as '" + untranslatedBy + "' leaves them"
                : "translated as " + translatedAs(catalogue.catchAll().translation()))
                              + (check.uncatalogued().dropped() > 0 || check.uncatalogued().mayBeDropped() > 0
                ? " (" + check.uncatalogued().unknown() + " of them; the build's mapping's drop rules drop the other "
                  + check.uncatalogued().dropped() + (check.uncatalogued().mayBeDropped() > 0
                        ? ", and may drop " + check.uncatalogued().mayBeDropped()
                        : "") + ")"
                : "");
        if (notRecorded != null) {
            // Seen in testing: a drop proposal refused, the form shown again, declined, and the agent sent back to
            // the same proposal, round and round.
            return "The user declined the catch-all: they don't want the " + check.uncatalogued().records()
                   + " uncatalogued records " + become + ". Your latest proposals for them were not recorded ("
                   + notRecorded + "): nothing is confirmed. Correct those proposals and send them again ("
                   + NAME + " build=" + build + " proposals=...), rather than asking the user again; ask them in "
                   + "the chat only what you can't tell.";
        }
        return "The user declined the catch-all: they don't want the " + check.uncatalogued().records()
               + " uncatalogued records (those no event type recognises) " + become + ". Nothing is confirmed, and "
               + "the "
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
