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
        name = ToEpoch.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Epoch",
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time since the epoch.",
        signatures = @FunctionSignature(
                description = "Gets the time of a date since the epoch (1 Jan 1970). Any part of " +
                              "the unit is dropped.",
                args = {
                        @FunctionArg(
                                name = "time",
                                argType = ValDate.class,
                                description = "The date and time."),
                        @FunctionArg(
                                name = "unit",
                                argType = ValString.class,
                                description = "The unit of the result, which is one of s, ms, us and ns.")}))
class ToEpoch extends AbstractCalendarFunction {

    static final String NAME = "toEpoch";

    public ToEpoch(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 2);
    }

    @Override
    int getZonePos(final Param[] params) {
        return NO_ZONE;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        if (pos == 1) {
            EpochUnit.fromName(val.toString());
        } else {
            DateReader.check(val);
        }
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        return ValLong.create(EpochUnit.fromName(vals[1].toString())
                .fromMillis(DateReader.readMillis(vals[0], zone)));
    }
}
