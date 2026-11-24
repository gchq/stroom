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

package stroom.query.api.datasource;

/// Option sets for testing the generic query option code. They have the kinds of options and rules a
/// data source might define (mutually exclusive options, options that only apply when another has a
/// value, chains of rules, integer ranges) but no real data source's options.
final class QueryOptionSetFixtures {

    static final String TOGGLE = "toggle";
    static final String LEVEL = "level";
    static final String LENGTH = "length";
    static final String SWITCH = "switch";
    static final String LIMIT = "limit";
    static final String MODE = "mode";
    static final String SPREAD = "spread";
    static final String JOIN = "join";

    static final String LEVEL_OFF = "OFF";
    static final String LEVEL_AUTO = "AUTO";
    static final String MODE_NORMAL = "NORMAL";
    static final String MODE_STRICT = "STRICT";
    static final String JOIN_ANY = "ANY";
    static final String JOIN_ALL = "ALL";

    /// The toggle and the level exclude each other; the length and switch only apply while the level is on.
    static final QueryOptionSet SET_A = QueryOptionSet.of(
            QueryOptionDefinition.builder()
                    .id(TOGGLE)
                    .displayName("Toggle")
                    .booleanType(false)
                    .addRule(QueryOptionRule.disabledUnless(LEVEL, LEVEL_OFF))
                    .build(),
            level()
                    .addRule(QueryOptionRule.disabledWhen(TOGGLE, "true"))
                    .build(),
            length(),
            QueryOptionDefinition.builder()
                    .id(SWITCH)
                    .displayName("Switch")
                    .booleanType(true)
                    .addRule(QueryOptionRule.disabledWhen(LEVEL, LEVEL_OFF))
                    .build());

    /// Strict mode enables the spread and disables the join and the level, and so the options that depend
    /// on the level.
    static final QueryOptionSet SET_B = QueryOptionSet.of(
            QueryOptionDefinition.builder()
                    .id(MODE)
                    .displayName("Mode")
                    .choiceType(MODE_NORMAL)
                    .addChoice(MODE_NORMAL, "Normal")
                    .addChoice(MODE_STRICT, "Strict")
                    .build(),
            QueryOptionDefinition.builder()
                    .id(SPREAD)
                    .displayName("Spread")
                    .integerType(0, 0, 100)
                    .addRule(QueryOptionRule.disabledUnless(MODE, MODE_STRICT))
                    .build(),
            QueryOptionDefinition.builder()
                    .id(JOIN)
                    .displayName("Join")
                    .choiceType(JOIN_ANY)
                    .addChoice(JOIN_ANY, "Any")
                    .addChoice(JOIN_ALL, "All")
                    .addRule(QueryOptionRule.disabledWhen(MODE, MODE_STRICT))
                    .build(),
            level()
                    .addRule(QueryOptionRule.disabledWhen(MODE, MODE_STRICT))
                    .build(),
            length(),
            QueryOptionDefinition.builder()
                    .id(LIMIT)
                    .displayName("Limit")
                    .integerType(50, 1, 1000)
                    .addRule(QueryOptionRule.disabledWhen(LEVEL, LEVEL_OFF))
                    .build());

    private QueryOptionSetFixtures() {
        // Utility class.
    }

    private static QueryOptionDefinition.Builder level() {
        return QueryOptionDefinition.builder()
                .id(LEVEL)
                .displayName("Level")
                .choiceType(LEVEL_OFF)
                .addChoice(LEVEL_OFF, "Off")
                .addChoice(LEVEL_AUTO, "Auto")
                .addChoice("1", "1")
                .addChoice("2", "2");
    }

    private static QueryOptionDefinition length() {
        return QueryOptionDefinition.builder()
                .id(LENGTH)
                .displayName("Length")
                .integerType(0, 0, 20)
                .addRule(QueryOptionRule.disabledWhen(LEVEL, LEVEL_OFF))
                .build();
    }
}
