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

@FunctionDef(
        name = FloorTime.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.ROUND_SUB_CATEGORY,
        commonDescription = "Floors the supplied time to the nearest duration.",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures =
        @FunctionSignature(
                description = "Floors the supplied time to the nearest duration.",
                args = {
                        @FunctionArg(
                                name = "time",
                                description = "The time to floor.",
                                argType = ValDate.class),
                        @FunctionArg(
                                name = "duration",
                                description = "The duration to floor by in ISO-8601 format " +
                                              "(e.g., 'PT5M' for 5 minutes). Multiples of it are " +
                                              "counted from midnight in the query's time zone.",
                                argType = ValString.class)
                })
)
class FloorTime extends AbstractRoundTimeToDuration {

    static final String NAME = "floorTime";

    public FloorTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    long adjust(final long millis, final long durationMillis) {
        return millis - remainder(millis, durationMillis);
    }
}
