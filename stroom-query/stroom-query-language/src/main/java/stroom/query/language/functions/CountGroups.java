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

import stroom.query.language.functions.ref.StoredValues;

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CountGroups.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonDescription = "This is used to count the number of unique group keys where there are multiple " +
                "group levels. For example if records are grouped by Name then Type then for each Name, " +
                "countGroups will give you the number of unique Type values for that Name.",
        commonReturnType = ValLong.class,
        commonReturnDescription = "Number of unique child group keys within the current group.",
        signatures = @FunctionSignature(
                args = {}))
class CountGroups extends AbstractFunction implements AggregateFunction {

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

    @Override
    public boolean requiresChildData() {
        return true;
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractNoChildGenerator {

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            Val val = null;
            if (childDataSupplier != null) {
                final ChildData childData = childDataSupplier.get();
                if (childData != null) {
                    final long count = childData.count();
                    if (count > 0) {
                        val = ValLong.create(count);
                    }
                }
            }

            if (val == null) {
                val = ValNull.INSTANCE;
            }

            return val;
        }
    }
}
