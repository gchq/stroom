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
        name = ToDate.NAME,
        commonCategory = FunctionCategory.CAST,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The value as the date type.",
        signatures = @FunctionSignature(
                description = "Converts the supplied value to a date (if it can be). A number is " +
                              "milliseconds since the epoch, and text is an ISO 8601 date with " +
                              "an optional time and offset. Text with no offset is in the time " +
                              "zone of the query.",
                args = {
                        @FunctionArg(
                                name = "value",
                                argType = Val.class,
                                description = "Field, the result of another function or a constant.")}))
class ToDate extends AbstractCalendarFunction {

    static final String NAME = "toDate";

    public ToDate(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 1);
    }

    @Override
    int getZonePos(final Param[] params) {
        return NO_ZONE;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        DateReader.check(val);
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        return ValDate.create(DateReader.readMillis(vals[0], zone));
    }
}
