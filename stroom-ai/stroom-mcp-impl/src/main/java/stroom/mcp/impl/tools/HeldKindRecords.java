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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.sample.HeldText;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// A sample's records with the fields the draft reads inside their held values (the keys of a JSON string, the
/// elements of XML text, the pairs its extractions take from text), for catalogue_event_types to give as evidence among
/// them as well as among the records' own: asked for by the user (seen in testing, 2026-11-18: an aggregated feed
/// whose source type was read inside the JSON string `event`, and only its outer fields, both times, put to the user
/// as the kinds of event). Named as the draft names them (`event.logger_name`), so a kind field chosen inside a held
/// value is one the draft's rules, and the translation, read.
final class HeldKindRecords {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HeldKindRecords.class);

    private HeldKindRecords() {
        // Static only.
    }

    /// @param samples The sample texts, by name.
    /// @param source  The source's name, as the draft is given it.
    /// @param splitter The Data Splitter the records are read with, or null.
    /// @param records The records as read (not changed).
    /// @return The records as given where none holds structured data in a string field; else the records read again
    /// with the fields the draft reads inside their held values added.
    static List<Map<String, Object>> of(final Map<String, String> samples,
                                        final String source,
                                        final SplitterSpec splitter,
                                        final List<Map<String, Object>> records) {
        if (records.stream().allMatch(record -> HeldText.expand(record).values().isEmpty())) {
            return records;
        }
        try {
            final Map<String, Object> draft = DraftMapping.draft(List.copyOf(samples.values()), source, null, null,
                    null, null, splitter);
            @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get(
                    "mapping");
            return mapping == null
                    ? records
                    : unpacked(DraftMapping.records(samples, splitter).records(), mapping);
        } catch (final RuntimeException e) {
            LOGGER.debug("No held fields read for the kind field: {}", e.getMessage());
            return records;
        }
    }

    /// The records with the fields the draft reads inside an inner message: the keys of JSON text (json_fields),
    /// the elements of XML text (xml_fields) and the values its extractions take from text (key/value pairs, say),
    /// as the translation reads them. Seen in testing: kinds told apart inside a message field listed with no
    /// records or sample, and no user field to name as the actor.
    ///
    /// @param records The records (changed in place).
    /// @param mapping The draft's mapping.
    /// @return The records.
    static List<Map<String, Object>> unpacked(final List<Map<String, Object>> records,
                                              final Map<String, Object> mapping) {
        final List<Object> held = new ArrayList<>(records);
        LocalCheck.withJsonFields(held, JsonValues.strings(mapping.get("json_fields")));
        LocalCheck.withXmlFields(held, JsonValues.strings(mapping.get("xml_fields")));
        // The draft's extractions alone, each read on its own: the rest of the draft may still be unfinished (seen in
        // chat 171: its time left for the agent to choose, the whole mapping refused, and every field the draft read
        // from NetScaler's message bodies missing from the listing).
        final List<Extraction> extractions = new ArrayList<>();
        for (final Map<String, Object> entry : JsonValues.maps(mapping.get("extract"))) {
            try {
                extractions.add(Extraction.fromJson(entry));
            } catch (final RuntimeException e) {
                LOGGER.info("An extraction of the draft can't be read, so it isn't applied: {}", e.getMessage());
            }
        }
        if (extractions.isEmpty()) {
            return records;
        }
        for (final Map<String, Object> record : records) {
            final Map<String, String> extracted = LocalCheck.extractedValues(extractions, record);
            if (extracted != null) {
                extracted.forEach((name, value) -> {
                    if (!value.isEmpty()) {
                        record.putIfAbsent(name, value);
                    }
                });
            }
        }
        return records;
    }
}
