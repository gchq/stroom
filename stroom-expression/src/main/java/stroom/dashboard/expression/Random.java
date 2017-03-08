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

package stroom.dashboard.expression;

import java.text.ParseException;

public class Random extends AbstractFunction {
    public static final String NAME = "random";

    public Random(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);
    }

    @Override
    public Generator createGenerator() {
        return new Gen();
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    private static class Gen extends AbstractNoChildGenerator {
        private static final long serialVersionUID = -7551073465232523106L;

        private Object value;

        @Override
        public void set(final String[] values) {
            value = Math.random();
        }

        @Override
        public Object eval() {
            return value;
        }
    }
}
