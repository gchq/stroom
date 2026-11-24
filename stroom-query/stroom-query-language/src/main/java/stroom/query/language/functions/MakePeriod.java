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

package stroom.query.language.functions;

import java.time.ZoneId;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = MakePeriod.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Period",
        commonReturnType = ValPeriod.class,
        commonReturnDescription = "The period.",
        signatures = @FunctionSignature(
                description = "Makes a period of years, months and days.",
                args = {
                        @FunctionArg(
                                name = "years",
                                argType = ValInteger.class,
                                description = "The years."),
                        @FunctionArg(
                                name = "months",
                                argType = ValInteger.class,
                                description = "The months."),
                        @FunctionArg(
                                name = "days",
                                argType = ValInteger.class,
                                description = "The days.")}))
class MakePeriod extends AbstractCalendarFunction {

    static final String NAME = "makePeriod";
    private static final String[] PART_NAMES = {"years", "months", "days"};

    public MakePeriod(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 3, 3);
    }

    @Override
    int getZonePos(final Param[] params) {
        return NO_ZONE;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        toInt(val, PART_NAMES[pos]);
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        return ValPeriod.create(
                toInt(vals[0], PART_NAMES[0]),
                toInt(vals[1], PART_NAMES[1]),
                toInt(vals[2], PART_NAMES[2]));
    }
}
