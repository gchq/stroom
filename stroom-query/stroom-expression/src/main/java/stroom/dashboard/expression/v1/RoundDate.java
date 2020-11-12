/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

abstract class RoundDate extends AbstractFunction {
    private Function function;

    public RoundDate(final String name) {
        super(name, 1, 1);
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
        return new RoundGenerator(childGenerator, getCalculator());
    }

    @Override
    public boolean hasAggregate() {
        return function.hasAggregate();
    }

    protected abstract RoundCalculator getCalculator();

    public abstract static class RoundDateCalculator implements RoundCalculator {
        private static final long serialVersionUID = 1099553839843710283L;

        @Override
        public Val calc(final Val value) {
            final Long val = value.toLong();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(val), ZoneOffset.UTC);
            dateTime = adjust(dateTime);
            return ValLong.create(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        }

        protected abstract LocalDateTime adjust(LocalDateTime dateTime);
    }
}
