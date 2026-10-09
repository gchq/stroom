/*
 * Copyright 2020 Crown Copyright
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

    // STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master. constantString below, and the three
    // parseDuration methods in FloorTime/CeilingTime/RoundTime that call it, are this fork's.
    // origin/master reads the duration argument with a bare param.toString(), which renders a
    // *function* as its own source text - so floorTime(x, param('w')) parses "param('w')" as a
    // Duration, throws, and yields ValErr for every row. That fails silently: a ValErr cell is not a
    // search error, so nothing is reported and the caller just sees no data. An incoming version
    // restores that behaviour without failing to compile, which is why this is marked rather than
    // left to be noticed.
    //
    // Raised with the Plan B maintainer via docs/planb-explicit-read-mode-proposal.md; drop this if
    // it is fixed upstream.

    /**
     * The value of a parameter that is constant, however it was written.
     *
     * <p>A literal arrives as a {@link Val} and is returned as it always was. A {@code param('key')}
     * arrives as a {@link Function} whose generator has been replaced by a {@link StaticValueGen}
     * holding the resolved value — which happens before any row is evaluated — so its value can be
     * read here.</p>
     *
     * <p>Returns {@code null} for anything genuinely computed per row, and that is deliberate rather
     * than a gap: a duration that differs from row to row has no meaning for rounding, so the caller
     * reports it as an invalid duration exactly as before. Compare {@code XPath.constantValue},
     * which resolves a constant argument the same way and for the same reason.</p>
     *
     * @param param the parameter to read; may be null
     * @return its value where constant, otherwise {@code null}
     */
    protected String constantString(final Param param) {
        if (param == null) {
            return null;
        }
        if (param instanceof final Function function) {
            if (function.createGenerator() instanceof final StaticValueGen staticValueGen) {
                final Val val = staticValueGen.eval(null, null);
                return val.type().isValue()
                        ? val.toString()
                        : null;
            }
            return null;
        }
        return param.toString();
    }

    /**
     * Why {@link #constantString} could not read a parameter, phrased for whoever wrote the query.
     *
     * <p>Two failures look identical from the call site and need different answers: a
     * {@code param('key')} that nobody supplied a value for, and an argument that genuinely varies
     * per row. Telling a user with an unmapped parameter that their argument "must be a constant"
     * sends them to fix the wrong thing.</p>
     */
    protected String whyNotConstant(final Param param) {
        if (param instanceof final Function function
            && function.createGenerator() instanceof StaticValueGen) {
            return "no value was supplied for it";
        }
        return "it must be a literal or a query parameter, not something computed per row";
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
