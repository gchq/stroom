/*
 * Copyright 2025 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.language.functions.TimeRounder.Mode;

@FunctionDef(
        name = RoundTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.ROUND_SUB_CATEGORY,
        commonDescription = "Rounds the supplied time to the nearest duration.",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures = {
                @FunctionSignature(
                        description = "Rounds the supplied time to the nearest duration. Multiples of it are " +
                                "counted from " +
                                      "midnight in the query's time zone.",
                        args = {
                                @FunctionArg(
                                        name = "time",
                                        description = "The time to round.",
                                        argType = ValDate.class),
                                @FunctionArg(
                                        name = "duration",
                                        description = "The duration to round by, e.g. 15m, or 'PT15M' in ISO-8601 " +
                                                      "format.",
                                        argType = Val.class)}),
                @FunctionSignature(
                        description = "Rounds the supplied time to the nearest duration. Multiples of it are " +
                                "counted from " +
                                      "the origin.",
                        args = {
                                @FunctionArg(
                                        name = "time",
                                        description = "The time to round.",
                                        argType = ValDate.class),
                                @FunctionArg(
                                        name = "duration",
                                        description = "The duration to round by, e.g. 15m, or 'PT15M' in ISO-8601 " +
                                                      "format.",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "origin",
                                        description = "The time to count multiples of the duration from.",
                                        argType = ValDate.class)})})
class RoundTime extends AbstractRoundTimeToDuration {

    static final String NAME = "roundTime";

    public RoundTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    Mode getMode() {
        return Mode.ROUND;
    }
}
