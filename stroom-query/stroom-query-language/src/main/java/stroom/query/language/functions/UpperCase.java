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
        name = UpperCase.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The string in upper case.",
        signatures = @FunctionSignature(
                description = "Convert the supplied string to upper case.",
                args = {
                        @FunctionArg(
                                name = "value",
                                description = "The string to convert to upper case.",
                                argType = ValString.class)}))
class UpperCase extends AbstractStringFunction {

    static final String NAME = "upperCase";

    private static final Operation OPERATION = String::toUpperCase;

    public UpperCase(final String name) {
        super(name);
    }

    @Override
    Operation getOperation() {
        return OPERATION;
    }
}
