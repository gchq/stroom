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

import java.text.ParseException;

@FunctionDef(
        name = Nth.NAME,
        commonCategory = FunctionCategory.SELECTION,
        commonReturnType = Val.class,
        signatures = @FunctionSignature(
                description = "Selects the Nth value in a set of grouped values. If there is no explicit ordering " +
                        "on the field selected then the value returned is indeterminate. E.g. for values " +
                        "[20, 40, 10, 30, 50], " + Nth.NAME + "(${field}, 2) returns 40.",
                returnDescription = "The Nth value of the grouped set.",
                args = {
                        @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = Val.class),
                        @FunctionArg(
                                name = "position",
                                description = "The position of the value in the grouped set to select. " +
                                        "Position is one based.",
                                argType = ValInteger.class)
                }))
public class Nth extends AbstractSelectorFunction {

    static final String NAME = "nth";

    private int pos;

    public Nth(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        if (params.length >= 2) {
            pos = ParamParseUtil.parseIntParam(params, 1, name, true);
            // Adjust for 0 based index.
            pos--;
        }
        super.setParams(params);
    }

    @Override
    public Generator createGenerator() {
        return new NthSelector(super.createGenerator(), pos);
    }

    static class NthSelector extends Selector {

        private final int pos;

        NthSelector(final Generator childGenerator, final int pos) {
            super(childGenerator);
            this.pos = pos;
        }

        @Override
        Val select(final Generator childGenerator,
                   final ChildData childData) {
            final StoredValues storedValues = childData.nth(pos);
            if (storedValues == null) {
                return null;
            }
            return childGenerator.eval(storedValues, () -> childData);
        }
    }
}
