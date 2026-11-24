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
import java.util.regex.Pattern;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ToDuration.NAME,
        commonCategory = FunctionCategory.CAST,
        commonReturnType = ValDuration.class,
        commonReturnDescription = "The value as the duration type.",
        signatures = @FunctionSignature(
                description = "Converts the supplied value to a duration (if it can be). A " +
                              "number is milliseconds, and text is as parseDuration reads it, except that 1M is a " +
                              "month and so is not a duration.",
                args = {
                        @FunctionArg(
                                name = "value",
                                argType = Val.class,
                                description = "Field, the result of another function or a constant.")}))
class ToDuration extends AbstractCalendarFunction {

    static final String NAME = "toDuration";
    // A number and M, which is months everywhere but in an ISO 8601 duration such as PT1M.
    private static final Pattern MONTHS_PATTERN = Pattern.compile("\\d\\s*+M(?![A-Za-z])");

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
        if (Type.DATE.equals(val.type())) {
            throw new IllegalArgumentException("A date is not a duration");
        }
        // parseDuration would read it as minutes.
        if (val instanceof ValString && isMonths(val.toString())) {
            throw new IllegalArgumentException("A period of months or years can't be used as a duration");
        }
        final Val duration = ValDurationUtil.parseDuration(val);
        if (duration instanceof ValDuration) {
            return duration;
        }
        throw new IllegalArgumentException(duration instanceof final ValErr err
                ? err.getMessage()
                : "A " + val.type().getName() + " is not a duration");
    }

    private static boolean isMonths(final String text) {
        return !text.contains("P") && MONTHS_PATTERN.matcher(text).find();
    }
}
