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
        name = ToDuration.NAME,
        commonCategory = FunctionCategory.CAST,
        commonReturnType = ValDuration.class,
        commonReturnDescription = "The value as the duration type.",
        signatures = @FunctionSignature(
                description = "Converts the supplied value to a duration (if it can be). A " +
                              "number is milliseconds, and text is as parseDuration reads it.",
                args = {
                        @FunctionArg(
                                name = "value",
                                argType = Val.class,
                                description = "Field, the result of another function or a constant.")}))
class ToDuration extends AbstractCalendarFunction {

    static final String NAME = "toDuration";

    public ToDuration(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 1);
    }

    @Override
    int getZonePos(final Param[] params) {
        return NO_ZONE;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        toDuration(val);
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        return toDuration(vals[0]);
    }

    private static Val toDuration(final Val val) {
        final Val duration = ValDurationUtil.toDuration(val);
        if (duration instanceof final ValErr err) {
            throw new IllegalArgumentException(err.getMessage());
        }
        return duration;
    }
}
