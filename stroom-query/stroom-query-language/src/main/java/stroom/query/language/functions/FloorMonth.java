/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;

import java.time.ZonedDateTime;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = FloorMonth.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.FLOOR_SUB_CATEGORY,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time down to the start of the current month.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round.",
                        argType = Val.class)))
class FloorMonth extends AbstractRoundDateTime {

    static final String NAME = "floorMonth";

    public FloorMonth(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        return FloorMonth::floor;
    }

    public static ZonedDateTime floor(final ZonedDateTime zonedDateTime) {
        return zonedDateTime
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }
}
