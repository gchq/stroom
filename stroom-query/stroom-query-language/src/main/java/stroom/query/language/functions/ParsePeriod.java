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

import java.text.ParseException;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ParsePeriod.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Period",
        commonReturnType = ValPeriod.class,
        commonReturnDescription = "The parsed period.",
        signatures = @FunctionSignature(
                description = "Parses the supplied value as a period of years, months, weeks and days, " +
                        "e.g. 'P1Y2M3D' or '1y2M3d'.",
                args = @FunctionArg(
                        name = "value",
                        argType = ValString.class,
                        description = "The period as a string.")))
class ParsePeriod extends AbstractCast {

    static final String NAME = "parsePeriod";
    private static final AbstractCaster CASTER = new Caster();

    public ParsePeriod(final String name) {
        super(name);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // Report what can be seen to be wrong now, rather than as an error in every row.
        if (params[0] instanceof final Val val && ValPeriodUtil.parsePeriod(val) instanceof final ValErr err) {
            throw new ParseException(err.getMessage(), 0);
        }
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
