/*
 * Copyright 2025 Crown Copyright
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

import java.time.LocalDate;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = InRange.NAME,
        commonCategory = FunctionCategory.LOGIC,
        commonDescription = "Returns true if the value is between the lower and upper bounds, inclusive. " +
                "All parameters must be either numbers, dates, durations or ISO date strings.",
        commonReturnType = ValBoolean.class,
        signatures = @FunctionSignature(
                description = "Returns true if the value is between lower and upper (inclusive). " +
                              "All parameters must be either numbers, dates, durations or ISO date strings.",
                args = {
                        @FunctionArg(name = "value", argType = Val.class, description = "The value to test."),
                        @FunctionArg(name = "lower", argType = Val.class, description = "The lower bound."),
                        @FunctionArg(name = "upper", argType = Val.class, description = "The upper bound.")
                }
        )
)
public class InRange extends AbstractManyChildFunction {

    static final String NAME = "inRange";

    public InRange() {
        super(NAME, 3, 3);
    }

    public InRange(final String name) {
        super(name, 3, 3);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractManyChildGenerator {

        private static final String TYPES_MESSAGE =
                "Parameters must be all numeric or all ISO date strings for between function";

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val value = childGenerators[0].eval(storedValues, childDataSupplier);
            final Val lower = childGenerators[1].eval(storedValues, childDataSupplier);
            final Val upper = childGenerators[2].eval(storedValues, childDataSupplier);

            if (Val.isNull(value) || Val.isNull(lower) || Val.isNull(upper)) {
                return ValErr.create("Null parameter in between function");
            }

            // Check if all are date strings
            if (value instanceof ValString && lower instanceof ValString && upper instanceof ValString) {
                try {
                    final LocalDate v = LocalDate.parse(value.toString());
                    final LocalDate l = LocalDate.parse(lower.toString());
                    final LocalDate u = LocalDate.parse(upper.toString());
                    return ValBoolean.create((!v.isBefore(l)) && (!v.isAfter(u)));
                } catch (final Exception e) {
                    return ValErr.create("Parameters must be valid ISO date strings for between function");
                }
            }

            ValErr notComparable = DateArithmetic.checkOrder(value, lower);
            if (notComparable == null) {
                notComparable = DateArithmetic.checkOrder(value, upper);
            }
            if (notComparable != null) {
                return notComparable;
            }

            // Check if all are numeric
            if (isNumeric(value) && isNumeric(lower) && isNumeric(upper)) {
                try {
                    final double v = value.toDouble();
                    final double l = lower.toDouble();
                    final double u = upper.toDouble();
                    return ValBoolean.create(l <= v && v <= u);
                } catch (final Exception e) {
                    return ValErr.create(TYPES_MESSAGE);
                }
            }

            return ValErr.create(TYPES_MESSAGE);
        }

        private static boolean isNumeric(final Val val) {
            return val instanceof ValNumber
                   || (val instanceof final ValPeriod period && period.hasOnlyDays());
        }
    }
}
