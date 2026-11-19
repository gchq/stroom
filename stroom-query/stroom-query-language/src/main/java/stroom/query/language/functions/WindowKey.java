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

/// The key of the window that a row is in, which rows of a query with a `window` clause are grouped
/// by. Two windows can start at the same time, on a day when the clocks go forward, so rows are
/// grouped by this and not by `windowStart()`.
@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = WindowKey.NAME,
        commonCategory = FunctionCategory.DATE,
        commonReturnType = ValLong.class,
        commonReturnDescription = "A number that identifies the row's window.",
        signatures = @FunctionSignature(
                description = "A number that identifies the time window that a row is in, for a query with "
                              + "a window clause. Later windows have higher numbers. Rows are grouped by it "
                              + "without it being written. Use windowStart() and windowEnd() for the times "
                              + "of the window.",
                args = {}))
class WindowKey extends AbstractWindowFunction {

    static final String NAME = "windowKey";

    public WindowKey(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 0, 0);
    }

    @Override
    public Generator createGenerator() {
        return new Gen(getWindowKeyReference());
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractWindowGenerator {

        private Gen(final ValReference windowKeyReference) {
            super(windowKeyReference);
        }

        @Override
        Val eval(final long windowKey) {
            return ValLong.create(windowKey);
        }
    }
}
