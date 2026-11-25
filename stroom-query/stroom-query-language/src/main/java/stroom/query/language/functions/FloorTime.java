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
        name = FloorTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.ROUND_SUB_CATEGORY,
        commonDescription = "Rounds the supplied time down to a multiple of a duration or period.",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures = {
                @FunctionSignature(
                        description = "Rounds the supplied time down to a multiple of a duration or period. " +
                                "Multiples are counted from midnight on 1 January 1970 in the time zone of the " +
                                "query, except that whole weeks, such as 1w or 7d, start on a Monday.",
                        args = {
                                @FunctionArg(
                                        name = "time",
                                        description = "The date, a number of milliseconds since the epoch (1 Jan " +
                                                "1970), or text of either. Text that is not a date gives a null.",
                                        argType = ValDate.class),
                                @FunctionArg(
                                        name = "duration",
                                        description = "The duration or period to floor by, e.g. 15m, 1d or 3M, or " +
                                                "ISO 8601 text such as 'PT15M'. Days in ISO text are 24 hours, so " +
                                                "'P7D' is not a week from a Monday.",
                                        argType = Val.class)}),
                @FunctionSignature(
                        description = "Rounds the supplied time down to a multiple of a duration or period. " +
                                "Multiples are counted from the origin.",
                        args = {
                                @FunctionArg(
                                        name = "time",
                                        description = "The date, a number of milliseconds since the epoch (1 Jan " +
                                                "1970), or text of either. Text that is not a date gives a null.",
                                        argType = ValDate.class),
                                @FunctionArg(
                                        name = "duration",
                                        description = "The duration or period to floor by, e.g. 15m, 1d or 3M, or " +
                                                "ISO 8601 text such as 'PT15M'. Days in ISO text are 24 hours, so " +
                                                "'P7D' is not a week from a Monday.",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "origin",
                                        description = "The time to count multiples from.",
                                        argType = ValDate.class)})})
class FloorTime extends AbstractRoundTimeToDuration {

    static final String NAME = "floorTime";

    public FloorTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    Mode getMode() {
        return Mode.FLOOR;
    }
}
