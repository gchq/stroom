/*
 * Copyright 2016-2025 Crown Copyright
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

abstract class AbstractRoundDateTime extends AbstractDateTimeFunction {

    static final String CEILING_SUB_CATEGORY = "Ceiling";
    static final String FLOOR_SUB_CATEGORY = "Floor";
    static final String ROUND_SUB_CATEGORY = "Round";
    private Function function;

    public AbstractRoundDateTime(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 1);
    }

    public AbstractRoundDateTime(final ExpressionContext expressionContext,
                                 final String name,
                                 final int minParams,
                                 final int maxParams) {
        super(expressionContext, name, minParams, maxParams);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            function = new StaticValueFunction((Val) param);
        }
    }

    @Override
    public Generator createGenerator() {
        final Generator childGenerator = function.createGenerator();
        return new RoundGenerator(childGenerator, new RoundDateCalculator(zoneId, getAdjuster()));
    }

    @Override
    public boolean hasAggregate() {
        return function.hasAggregate();
    }

    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }

    protected abstract DateTimeAdjuster getAdjuster();

    public static final class RoundDateCalculator implements RoundCalculator {

        private final ZoneId zoneId;
        private final DateTimeAdjuster adjuster;

        RoundDateCalculator(final ZoneId zoneId,
                            final DateTimeAdjuster adjuster) {
            this.zoneId = zoneId;
            this.adjuster = adjuster;
        }

        @Override
        public Val calc(final Val value) {
            try {
                final Long val = value.toLong();
                if (val == null) {
                    return ValNull.INSTANCE;
                }

                ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(val), zoneId);
                dateTime = adjuster.adjust(dateTime);
                return ValDate.create(dateTime.toInstant().toEpochMilli());
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }

    protected Param[] getParams() {
        return params;
    }

    public interface DateTimeAdjuster {

        ZonedDateTime adjust(ZonedDateTime zonedDateTime);
    }
}
