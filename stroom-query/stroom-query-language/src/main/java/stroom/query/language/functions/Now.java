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
        name = Now.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Variable",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The current date and time.",
        signatures = @FunctionSignature(
                description = "Returns the current date and time.",
                args = {}))
class Now extends AbstractCurrentDateTimeFunction {

    static final String NAME = "now";

    private final Generator generator;

    public Now(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
        generator = new StaticValueGen(ValDate.create(expressionContext.getDateTimeSettings().getReferenceTime()));
    }

    @Override
    public Generator createGenerator() {
        return generator;
    }
}
