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
        name = InRange.NAME,
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = ValBoolean.class,
        signatures = @FunctionSignature(
                description = "Returns true if the value is between lower and upper (inclusive). " +
                              "All parameters must be either numbers or ISO date strings.",
                args = {
                        @FunctionArg(name = "value", argType = Val.class, description = "The value to test."),
                        @FunctionArg(name = "lower", argType = Val.class, description = "The lower bound."),
                        @FunctionArg(name = "upper", argType = Val.class, description = "The upper bound.")
                }
        )
)
public class InRange extends AbstractFunction {

    static final String NAME = "inRange";

    public InRange() {
        super(NAME, 3, 3);
    }

    public InRange(final String name) {
        super(name, 3, 3);
    }

    @Override
    public Generator createGenerator() {
        final Param valueParam = params[0];
        final Param lowerParam = params[1];
        final Param upperParam = params[2];

        return new Generator() {
            @Override
            public void set(final Val[] values, final StoredValues storedValues) {
                // No-op
            }

            @Override
            public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
                final Val value = valueParam instanceof Function
                        ? ((Function) valueParam).createGenerator().eval(storedValues, childDataSupplier)
                        : (Val) valueParam;
                final Val lower = lowerParam instanceof Function
                        ? ((Function) lowerParam).createGenerator().eval(storedValues, childDataSupplier)
                        : (Val) lowerParam;
                final Val upper = upperParam instanceof Function
                        ? ((Function) upperParam).createGenerator().eval(storedValues, childDataSupplier)
                        : (Val) upperParam;

                if (value == null || lower == null || upper == null
                    || value == ValNull.INSTANCE
                    || lower == ValNull.INSTANCE
                    || upper == ValNull.INSTANCE) {
                    return ValErr.create("Null parameter in between function");
                }

                // Check if all are date strings
                if (value instanceof ValString && lower instanceof ValString && upper instanceof ValString) {
                    try {
                        final java.time.LocalDate v = java.time.LocalDate.parse(value.toString());
                        final java.time.LocalDate l = java.time.LocalDate.parse(lower.toString());
                        final java.time.LocalDate u = java.time.LocalDate.parse(upper.toString());
                        return ValBoolean.create((!v.isBefore(l)) && (!v.isAfter(u)));
                    } catch (final Exception e) {
                        return ValErr.create("Parameters must be valid ISO date strings for between function");
                    }
                }

                // Check if all are numeric
                if (value instanceof ValNumber && lower instanceof ValNumber && upper instanceof ValNumber) {
                    try {
                        final double v = value.toDouble();
                        final double l = lower.toDouble();
                        final double u = upper.toDouble();
                        return ValBoolean.create(l <= v && v <= u);
                    } catch (final Exception e) {
                        return ValErr.create(
                                "Parameters must be all numeric or all ISO date strings for between function");
                    }
                }

                return ValErr.create("Parameters must be all numeric or all ISO date strings for between function");
            }

            @Override
            public void merge(final StoredValues existingValues, final StoredValues newValues) {
                // No-op
            }
        };
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }
}
