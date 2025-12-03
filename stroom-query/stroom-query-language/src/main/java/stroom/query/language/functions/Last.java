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

@FunctionDef(
        name = Last.NAME,
        commonCategory = FunctionCategory.SELECTION,
        commonReturnType = Val.class,
        commonReturnDescription = "",
        signatures = @FunctionSignature(
                description = "Selects the last value found in the group even if it is " +
                        Null.NAME + "() or " + Err.NAME + "(). " +
                        "If no explicit ordering is set then the value selected is indeterminate.",
                returnDescription = "The last value of the group.",
                args = {
                        @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = Val.class)
                }))
public class Last extends AbstractSelectorFunction {

    static final String NAME = "last";

    public Last(final String name) {
        super(name, 1, 1);
    }

    @Override
    public Generator createGenerator() {
        return new LastSelector(super.createGenerator());
    }

    static class LastSelector extends Selector {

        LastSelector(final Generator childGenerator) {
            super(childGenerator);
        }

        @Override
        Val select(final Generator childGenerator,
                   final ChildData childData) {
            final StoredValues storedValues = childData.last();
            if (storedValues == null) {
                return null;
            }

            return childGenerator.eval(storedValues, () -> childData);
        }
    }
}
