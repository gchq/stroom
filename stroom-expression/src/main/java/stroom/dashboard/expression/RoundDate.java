/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.expression;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public abstract class RoundDate extends AbstractFunction {
    public abstract static class RoundDateCalculator implements RoundCalculator {
        private static final long serialVersionUID = 1099553839843710283L;

        @Override
        public Double calc(final Double value) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(value.longValue()), ZoneOffset.UTC);
            dateTime = adjust(dateTime);
            return (double) dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        }

        protected abstract LocalDateTime adjust(LocalDateTime dateTime);
    }

    private Function function = null;

    public RoundDate(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        final Object param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            function = new StaticValueFunction(param);
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
}
