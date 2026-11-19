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

import stroom.query.language.functions.ref.ValReference;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = WindowStart.NAME,
        commonCategory = FunctionCategory.DATE,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The time the row's window starts.",
        signatures = @FunctionSignature(
                description = "The start of the time window that a row is in, for a query with a window "
                              + "clause, as in 'window EventTime by 1d'. The start is part of the window.",
                args = {}))
class WindowStart extends AbstractWindowFunction {

    static final String NAME = "windowStart";

    public WindowStart(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 0, 0);
    }

    @Override
    public Generator createGenerator() {
        return new Gen(getWindowKeyReference(), getTimeWindows());
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractWindowGenerator {

        private final TimeWindows timeWindows;

        private Gen(final ValReference windowKeyReference, final TimeWindows timeWindows) {
            super(windowKeyReference);
            this.timeWindows = timeWindows;
        }

        @Override
        Val eval(final long windowKey) {
            return ValDate.create(timeWindows.getWindowStart(windowKey));
        }
    }
}
