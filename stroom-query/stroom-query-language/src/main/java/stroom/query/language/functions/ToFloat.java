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


@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ToFloat.NAME,
        commonCategory = FunctionCategory.CAST,
        commonReturnType = ValFloat.class,
        commonReturnDescription = "The value as the float type.",
        signatures = @FunctionSignature(
                description = "Converts the supplied value to a float (if it can be). For example, converting " +
                        "the text \"1.2\" to the number 1.2",
                args = @FunctionArg(
                        name = "value",
                        description = "Field, the result of another function or a constant.",
                        argType = Val.class)))
class ToFloat extends AbstractCast {

    static final String NAME = "toFloat";
    private static final ValErr ERROR = ValErr.create("Unable to cast to a float");
    private static final Cast CAST = new Cast();

    public ToFloat(final String name) {
        super(name);
    }

    @Override
    AbstractCaster getCaster() {
        return CAST;
    }

    private static class Cast extends AbstractCaster {

        @Override
        Val cast(final Val val) {
            if (!val.type().isValue()) {
                return val;
            }

            final Double value = val.toDouble();
            if (value != null) {
                return ValFloat.create(value.floatValue());
            }
            return ERROR;
        }
    }
}
