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
        name = ToPeriod.NAME,
        commonCategory = FunctionCategory.CAST,
        commonReturnType = ValPeriod.class,
        commonReturnDescription = "The value as the period type.",
        signatures = @FunctionSignature(
                description = "Converts the supplied value to a period (if it can be). For example, converting " +
                        "the text \"P1Y2M3D\" to a period of 1 year, 2 months and 3 days.",
                args = @FunctionArg(
                        name = "value",
                        argType = Val.class,
                        description = "Field, the result of another function or a constant.")))
class ToPeriod extends AbstractCast {

    static final String NAME = "toPeriod";
    private static final AbstractCaster CASTER = new Caster();

    public ToPeriod(final String name) {
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
            return ValPeriodUtil.parsePeriod(val);
        }
    }
}
