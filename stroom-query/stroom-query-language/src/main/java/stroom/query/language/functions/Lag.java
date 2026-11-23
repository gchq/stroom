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

/// `lag(aggregate, windows[, default])`, the value an aggregate has in the window a number of
/// window sizes earlier than a row's own, for the same group.
@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Lag.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonReturnType = Val.class,
        commonReturnDescription = "The value the aggregate has in the earlier window.",
        commonDescription = "The value an aggregate has in an earlier time window of the same group, for a "
                            + "query with a window clause, as in 'window EventTime by 1d'. For example "
                            + "'lag(count(), 1)' is the count of the window before, so "
                            + "'count() - lag(count(), 1)' is the change from one window to the next. The "
                            + "earlier window is a number of window sizes before, whatever the advance.",
        signatures = {
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "aggregate",
                                        description = "An aggregate, such as count() or sum(${Bytes}).",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "windows",
                                        description = "How many windows earlier, as a whole number from 1.",
                                        argType = ValInteger.class)}),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "aggregate",
                                        description = "An aggregate, such as count() or sum(${Bytes}).",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "windows",
                                        description = "How many windows earlier, as a whole number from 1.",
                                        argType = ValInteger.class),
                                @FunctionArg(
                                        name = "default",
                                        description = "The value to give when the earlier window has none.",
                                        argType = Val.class)})})
class Lag extends AbstractWindowShiftFunction {

    static final String NAME = "lag";

    /// Creates the function.
    ///
    /// @param expressionContext The context the function is parsed in, which holds the query's windows.
    /// @param name              The name the function was written with.
    public Lag(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, false);
    }
}
