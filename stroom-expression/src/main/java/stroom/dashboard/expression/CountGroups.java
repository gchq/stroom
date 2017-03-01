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

import java.util.HashSet;
import java.util.Set;

public class CountGroups extends AbstractFunction {
    public static final String NAME = "countGroups";

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

    private static class Gen extends AbstractNoChildGenerator {
        private static final long serialVersionUID = -9130548669643582369L;

        private final Set<Object> childGroups = new HashSet<>();
        private long nonGroupedChildCount;

        @Override
        public Object eval() {
            final long count = nonGroupedChildCount + childGroups.size();
            if (count == 0) {
                return null;
            }

            return Double.valueOf(count);
        }

        @Override
        public void addChildKey(final Object key) {
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
    }
}
