/*
 * Copyright 2026 Crown Copyright
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
        name = FormatPeriod.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Period",
        commonReturnType = ValString.class,
        commonReturnDescription = "A period formatted as a string.",
        signatures = @FunctionSignature(
                description = "Formats the supplied period as an ISO 8601 string, e.g. 'P1Y2M3D'.",
                args = @FunctionArg(
                        name = "value",
                        argType = ValPeriod.class,
                        description = "The period.")))
class FormatPeriod extends AbstractCast {

    static final String NAME = "formatPeriod";
    private static final AbstractCaster CASTER = new Caster();

    public FormatPeriod(final String name) {
        super(name);
    }

    @Override
    AbstractCaster getCaster() {
        return CASTER;
    }


    // --------------------------------------------------------------------------------


    private static final class Caster extends AbstractCaster {

        @Override
        Val cast(final Val val) {
            return ValPeriodUtil.formatPeriod(val);
        }
    }
}
