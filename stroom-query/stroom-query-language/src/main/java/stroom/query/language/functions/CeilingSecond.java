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
        name = CeilingSecond.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.CEILING_SUB_CATEGORY,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time up to the start of the next second.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round.",
                        argType = Val.class)))
class CeilingSecond extends AbstractRoundDateTime {

    static final String NAME = "ceilingSecond";

    public CeilingSecond(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        return zonedDateTime -> {
            ZonedDateTime result = FloorSecond.floor(zonedDateTime);
            if (zonedDateTime.isAfter(result)) {
                result = result.plusSeconds(1);
            }
            return result;
        };
    }
}
