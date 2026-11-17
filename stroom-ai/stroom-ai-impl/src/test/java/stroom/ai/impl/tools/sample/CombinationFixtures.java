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

package stroom.ai.impl.tools.sample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Records of an aggregated feed whose source is told only by two fields together (decided 2026-11-17): the
/// wrapper's `system` (Teams, Skype for Business) and, inside the escaped JSON its `event` holds, `logger_name`
/// (System, Application). Each record's `event` has keys of its own by the pair, two kinds of event each, so the
/// shapes differ by the pair; variants make the same-shape, kind-like and single-field cases.
final class CombinationFixtures {

    /// The products.
    static final List<String> SYSTEMS = List.of("Teams", "Skype for Business");
    /// The loggers.
    static final List<String> LOGGERS = List.of("System", "Application");
    // Each pair's two kinds of event, by their keys.
    private static final String[][][] KEYS = {
            {{"\"thread\": \"t1\", \"cpu", "\"thread\": \"t1\", \"memory"},
                    {"\"user\": \"u1\", \"call_id", "\"user\": \"u1\", \"meeting_id"}},
            {{"\"pid\": \"9\", \"disk", "\"pid\": \"9\", \"network"},
                    {"\"sip_uri\": \"sip:a\", \"codec", "\"sip_uri\": \"sip:a\", \"jitter"}}};

    private CombinationFixtures() {
        // Static only.
    }

    /// @param perPair The records of each pair of system and logger.
    /// @return The records, parsed (flattened), each pair's `event` keys its own, two kinds each.
    static List<Map<String, Object>> byPair(final int perPair) {
        return records(perPair, Variant.BY_PAIR);
    }

    /// @param perPair The records of each pair.
    /// @param variant How the `event` keys are chosen.
    /// @return The records, parsed (flattened).
    static List<Map<String, Object>> records(final int perPair, final Variant variant) {
        final List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (int s = 0; s < SYSTEMS.size(); s++) {
            for (int l = 0; l < LOGGERS.size(); l++) {
                for (int i = 0; i < perPair; i++) {
                    final Map<String, Object> record = new LinkedHashMap<>();
                    record.put("timestamp", "2026-11-18T10:00:" + String.format("%02d", n % 60) + ".000Z");
                    record.put("hostname", "uc0" + n % 2);
                    record.put("system", SYSTEMS.get(s));
                    record.put("event", event(variant, s, l, i));
                    out.add(record);
                    n++;
                }
            }
        }
        return out;
    }

    /// @param variant How the keys are chosen.
    /// @param s       The system's index.
    /// @param l       The logger's index.
    /// @param i       The record's number within its pair.
    /// @return The escaped JSON the record's `event` holds.
    static String event(final Variant variant, final int s, final int l, final int i) {
        final String logger = switch (variant) {
            case UNIQUE_LOGGERS -> (s == 0
                    ? "com.teams."
                    : "com.skype.") + LOGGERS.get(l);
            default -> LOGGERS.get(l);
        };
        final String first = "{\"logger_name\": \"" + logger + "\", ";
        return switch (variant) {
            // The logger's records look alike within a system: their keys are the system's alone.
            case SAME_SHAPE -> first + (s == 0
                    ? "\"tenant\": \"t" + i % 3 + "\", \"" + (i % 2 == 0
                    ? "chat"
                    : "meeting") + "\": \"m" + i + "\"}"
                    : "\"pool\": \"p" + i % 3 + "\", \"" + (i % 2 == 0
                    ? "sip"
                    : "presence") + "\": \"x" + i + "\"}");
            // Each kind of the second field one shape: kinds, never families.
            case KIND_CODES -> "{\"code\": \"" + (s * 10 + l) + "\", \"" + (s == 0
                    ? "tenant"
                    : "pool") + "\": \"t" + i % 3 + "\", \"k" + s + l + "\": " + i + "}";
            default -> first + KEYS[s][l][i % 2] + "\": \"v" + i + "\", \"seq\": " + i + "}";
        };
    }


    // --------------------------------------------------------------------------------


    /// How the `event` keys are chosen.
    enum Variant {
        /// Keys of their own by the pair of system and logger.
        BY_PAIR,
        /// Keys by the system alone: the loggers look alike within a system.
        SAME_SHAPE,
        /// No logger: a `code` a pair, each one shape (kinds of event).
        KIND_CODES,
        /// Keys by the pair, the logger's name saying its system too (`com.teams.System`).
        UNIQUE_LOGGERS
    }
}
