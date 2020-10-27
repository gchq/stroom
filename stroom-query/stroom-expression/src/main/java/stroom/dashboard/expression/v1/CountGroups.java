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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.HashSet;
import java.util.Set;

class CountGroups extends AbstractFunction {
    static final String NAME = "countGroups";

    public CountGroups(final String name) {
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
        private static final long serialVersionUID = -9130548669643582369L;

        private final Set<Key> childGroups = new HashSet<>();
        private long nonGroupedChildCount;

        @Override
        public Val eval() {
            final long count = nonGroupedChildCount + childGroups.size();
            if (count == 0) {
                return ValNull.INSTANCE;
            }

            return ValLong.create(count);
        }

        @Override
        public void addChildKey(final Key key) {
            if (key == null) {
                nonGroupedChildCount++;
            } else {
                childGroups.add(key);
            }
        }

        @Override
        public void merge(final Generator generator) {
            final Gen countGen = (Gen) generator;
            nonGroupedChildCount += countGen.nonGroupedChildCount;
            childGroups.addAll(countGen.childGroups);
            super.merge(generator);
        }

        @Override
        public void read(final Kryo kryo, final Input input) {
            childGroups.clear();
            final int length = input.readInt(true);
            for (int i = 0; i < length; i++) {
                childGroups.add((Key) kryo.readClassAndObject(input));
            }
            nonGroupedChildCount = input.readLong(true);
        }

        @Override
        public void write(final Kryo kryo, final Output output) {
            output.writeInt(childGroups.size(), true);
            for (final Key key : childGroups) {
                kryo.writeClassAndObject(output, key);
            }
            output.writeLong(nonGroupedChildCount, true);
        }
    }
}
