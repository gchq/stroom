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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = DateDiff.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Calendar",
        commonReturnType = ValLong.class,
        commonReturnDescription = "The number of whole units.",
        signatures = @FunctionSignature(
                description = "Counts the whole units from one date to another. Days and longer units are counted on " +
                        "the calendar in a time zone, and shorter units are exact. 31 January to 1 February is 0 " +
                        "whole months. To count the months that start between two dates use dateDiff('month', " +
                        "floorMonth(start), floorMonth(end)).",
                args = {
                        @FunctionArg(
                                name = "unit",
                                argType = ValString.class,
                                description = "The unit to count, which is one of year, quarter, month, week, " +
                                              "day, hour, minute, second and millisecond."),
                        @FunctionArg(
                                name = "start",
                                argType = ValDate.class,
                                description = "The date to count from."),
                        @FunctionArg(
                                name = "end",
                                argType = ValDate.class,
                                description = "The date to count to."),
                        @FunctionArg(
                                name = "timezone",
                                argType = ValString.class,
                                isOptional = true,
                                description = "The time zone, e.g. 'Europe/London', which can be a field. The time " +
                                              "zone of the query if it is not given.")}))
class DateDiff extends AbstractCalendarFunction {

    static final String NAME = "dateDiff";

    public DateDiff(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 3, 4);
    }

    @Override
    int getZonePos(final Param[] params) {
        return 3;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        if (pos == 0) {
            DateDiffUnit.fromName(val.toString());
        } else {
            DateReader.check(val);
        }
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        final DateDiffUnit unit = DateDiffUnit.fromName(vals[0].toString());
        final Instant start = Instant.ofEpochMilli(DateReader.readMillis(vals[1], zone));
        final Instant end = Instant.ofEpochMilli(DateReader.readMillis(vals[2], zone));
        // The units that are exact are the same in every time zone.
        final ZoneId zoneId = unit.isExact()
                ? ZoneOffset.UTC
                : zone.get();
        return ValLong.create(unit.between(start.atZone(zoneId), end.atZone(zoneId)));
    }
}
