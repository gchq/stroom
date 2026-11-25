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

import stroom.query.api.DateTimeSettings;
import stroom.util.shared.NullSafe;

import java.time.ZoneId;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Ago.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Variable",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The date and time.",
        signatures = @FunctionSignature(
                description = "Gets the time that was a duration or period before now, in the time zone of the " +
                        "query. ago(1d) is the same as now() - 1d, which is 23 or 25 hours ago if the clocks have " +
                        "changed. Use ago(24h) for exactly 24 hours.",
                args = {
                        @FunctionArg(
                                name = "amount",
                                argType = Val.class,
                                description = "The duration or period, e.g. 2h or 1M, or text of one.")}))
class Ago extends AbstractCalendarFunction {

    static final String NAME = "ago";

    private final ExpressionContext expressionContext;

    public Ago(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 1);
        this.expressionContext = expressionContext;
    }

    @Override
    int getZonePos(final Param[] params) {
        return NO_ZONE;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        toAmount(val);
    }

    private static Val toAmount(final Val val) {
        if (val instanceof ValDuration || val instanceof ValPeriod) {
            return val;
        } else if (val instanceof ValString) {
            return TimeRounder.parseMultiple(val.toString());
        }
        throw new IllegalArgumentException("A " + val.type().getName() + " is not a duration or period");
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        final Long now = NullSafe.get(
                expressionContext,
                ExpressionContext::getDateTimeSettings,
                DateTimeSettings::getReferenceTime);
        if (now == null) {
            throw new IllegalArgumentException("referenceTime not set in searchRequest.dateTimeSettings");
        }
        final Val amount = toAmount(vals[0]);
        return DateArithmetic.subtract(ValDate.create(now), amount, zone);
    }
}
