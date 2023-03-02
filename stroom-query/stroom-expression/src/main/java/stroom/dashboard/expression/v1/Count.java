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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = "count",
        commonCategory = FunctionCategory.AGGREGATE,
        commonDescription = "Counts the number of records that are passed through it. Doesn't take any " +
                "notice of the values of any fields.",
        signatures = @FunctionSignature(
                returnType = ValLong.class,
                returnDescription = "Number of records",
                args = {}))
class Count extends AbstractFunction implements AggregateFunction {

    static final String NAME = "count";

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

    private static final class Gen extends AbstractNoChildGenerator {

        private long count;

        @Override
        public void set(final Values values) {
            count++;
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            return ValLong.create(count);
        }

        @Override
        public void merge(final Generator generator) {
            final Gen countGen = (Gen) generator;
            count += countGen.count;
            super.merge(generator);
        }

        @Override
        public void read(final Input input) {
            count = input.readLong(true);
        }

        @Override
        public void write(final Output output) {
            output.writeLong(count, true);
        }
    }
}
