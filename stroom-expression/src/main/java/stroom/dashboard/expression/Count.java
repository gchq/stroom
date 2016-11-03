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

public class Count extends AbstractFunction {
    private static class Gen extends AbstractNoChildGenerator {
        private static final long serialVersionUID = 9222017471352363944L;

        private double count;

        @Override
        public void set(final String[] values) {
            count++;
        }

        @Override
        public Object eval() {
            return count;
        }

        @Override
        public void merge(final Generator generator) {
            final Gen countGen = (Gen) generator;
            count += countGen.count;
            super.merge(generator);
        }
    }

    public static final String NAME = "count";

    public Count(final String name) {
        super(name, 0, 0);
    }

    @Override
    public Generator createGenerator() {
        return new Gen();
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }
}
