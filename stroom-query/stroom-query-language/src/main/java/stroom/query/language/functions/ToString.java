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
        name = ToString.NAME,
        commonCategory = FunctionCategory.CAST,
        commonReturnType = ValString.class,
        commonReturnDescription = "The value as the string type.",
        signatures = @FunctionSignature(
                description = "Converts the supplied value to a string, " +
                        "e.g. 1.23 becomes \"1.23\" and true() becomes \"true\".",
                args = @FunctionArg(
                        name = "value",
                        description = "Field, the result of another function or a constant.",
                        argType = Val.class)))
class ToString extends AbstractCast {

    static final String NAME = "toString";
    private static final ValErr ERROR = ValErr.create("Unable to cast to a string");
    private static final Cast CAST = new Cast();

    public ToString(final String name) {
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

            final String value = val.toString();
            if (value != null) {
                return ValString.create(value);
            }
            return ERROR;
        }
    }
}
