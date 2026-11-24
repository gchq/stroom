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

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = MakeDate.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Calendar",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The date and time.",
        signatures = @FunctionSignature(
                description = "Makes a date from its parts in a time zone. A time that the " +
                              "clocks skip moves forward by as much as they do.",
                args = {
                        @FunctionArg(
                                name = "year",
                                argType = ValInteger.class,
                                description = "The year."),
                        @FunctionArg(
                                name = "month",
                                argType = ValInteger.class,
                                description = "The month, from 1 to 12."),
                        @FunctionArg(
                                name = "day",
                                argType = ValInteger.class,
                                description = "The day of the month, from 1."),
                        @FunctionArg(
                                name = "hour",
                                argType = ValInteger.class,
                                isOptional = true,
                                description = "The hour, from 0 to 23."),
                        @FunctionArg(
                                name = "minute",
                                argType = ValInteger.class,
                                isOptional = true,
                                description = "The minute, from 0 to 59."),
                        @FunctionArg(
                                name = "second",
                                argType = ValInteger.class,
                                isOptional = true,
                                description = "The second, from 0 to 59."),
                        @FunctionArg(
                                name = "millisecond",
                                argType = ValInteger.class,
                                isOptional = true,
                                description = "The millisecond, from 0 to 999."),
                        @FunctionArg(
                                name = "timezone",
                                argType = ValString.class,
                                isOptional = true,
                                description = "The time zone, e.g. 'Europe/London', which can be a field if all " +
                                              "the parts are given. The time zone of the query if it is not " +
                                              "given.")}))
class MakeDate extends AbstractCalendarFunction {

    static final String NAME = "makeDate";
    private static final int MIN_PARTS = 3;
    private static final String[] PART_NAMES = {"year", "month", "day", "hour", "minute", "second", "millisecond"};

    public MakeDate(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 3, 8);
    }

    @Override
    int getZonePos(final Param[] params) {
        return params.length > PART_NAMES.length || isZoneName(params[params.length - 1])
                ? params.length - 1
                : NO_ZONE;
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final int zonePos = getZonePos(params);
        if (zonePos != NO_ZONE && zonePos < MIN_PARTS) {
            throw new ParseException(getTooFewPartsMessage(), 0);
        }
    }

    // Text that isn't a number, or that has a sign as an offset does, has to be a time zone.
    private static boolean isZoneName(final Param param) {
        if (param instanceof final ValString val) {
            final String text = val.toString().trim();
            return text.startsWith("+") || text.startsWith("-") || !TimeRounder.isNumber(text);
        }
        return false;
    }

    private String getTooFewPartsMessage() {
        return "'" + name + "' needs a year, month and day before the time zone";
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        toInt(val, PART_NAMES[pos]);
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        // A query parameter isn't known to be a time zone when the expression is parsed.
        if (vals.length < MIN_PARTS) {
            throw new IllegalArgumentException(getTooFewPartsMessage());
        }
        final int[] parts = new int[]{0, 1, 1, 0, 0, 0, 0};
        for (int i = 0; i < vals.length; i++) {
            parts[i] = toInt(vals[i], PART_NAMES[i]);
        }
        return ValDate.create(LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
                .with(ChronoField.MILLI_OF_SECOND, parts[6])
                .atZone(zone.get())
                .toInstant()
                .toEpochMilli());
    }
}
