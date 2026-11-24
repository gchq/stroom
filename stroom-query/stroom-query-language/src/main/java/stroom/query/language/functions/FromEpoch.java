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

import stroom.util.shared.NullSafe;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = FromEpoch.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Epoch",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The date and time.",
        signatures = @FunctionSignature(
                description = "Makes a date from the time since the epoch (1 Jan 1970).",
                args = {
                        @FunctionArg(
                                name = "amount",
                                argType = ValNumber.class,
                                description = "The time since the epoch."),
                        @FunctionArg(
                                name = "unit",
                                argType = ValString.class,
                                description = "The unit of the amount, which is one of s, ms, us and ns.")}))
class FromEpoch extends AbstractCalendarFunction {

    static final String NAME = "fromEpoch";

    public FromEpoch(final ExpressionContext expressionContext, final String name) {
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
        }
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        // There is no text for a number that is infinite or is not a number.
        final String amount = NullSafe.string(vals[0].toString()).trim();
        if (!TimeRounder.isNumber(amount)) {
            throw new IllegalArgumentException("'" + amount + "' is not a number");
        }
        return ValDate.create(EpochUnit.fromName(vals[1].toString()).toMillis(new BigDecimal(amount)));
    }
}
