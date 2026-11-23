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

import stroom.query.api.HoppingWindow;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValReference;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/// A function that works with the time windows of a query's `window` clause.
///
/// A query's author writes such a function with no mention of the windows, as in `windowStart()`.
/// When the query is parsed the function takes the windows from the [ExpressionContext], and from
/// then on it carries them as a last parameter of its own, as in
/// `windowStart('window:1d|1h||Europe/London')`. So wherever the parsed expression is compiled again,
/// on whichever node, the function has the windows it was written for without being given them.
abstract class AbstractWindowFunction extends AbstractFunction {

    private static final String SPEC_PREFIX = "window:";
    private static final String SPEC_SEPARATOR = "|";

    private final ExpressionContext expressionContext;
    private final int minOwnParams;
    private final int maxOwnParams;
    private HoppingWindow window;
    private TimeWindows timeWindows;
    private ValReference windowKeyReference;

    /// Creates the function.
    ///
    /// @param expressionContext The context the function is parsed in, which holds the query's windows.
    /// @param name              The name the function was written with.
    /// @param minParams         The fewest parameters the query's author must give.
    /// @param maxParams         The most parameters the query's author may give.
    AbstractWindowFunction(final ExpressionContext expressionContext,
                           final String name,
                           final int minParams,
                           final int maxParams) {
        // One more parameter is allowed for, which holds the windows.
        super(name, minParams, maxParams + 1);
        this.expressionContext = expressionContext;
        this.minOwnParams = minParams;
        this.maxOwnParams = maxParams;
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        Param[] ownParams = params;
        final String spec = getSpec(params);
        if (spec != null) {
            window = parseSpec(spec);
            ownParams = Arrays.copyOf(params, params.length - 1);
        } else if (expressionContext != null) {
            window = expressionContext.getWindow();
        }
        // The windows are held in a last parameter that starts with a prefix, so a value that the
        // query gives, such as a default, can't start with it.
        for (final Param ownParam : ownParams) {
            if (isSpec(ownParam)) {
                throw reservedValueException();
            }
        }
        if (spec != null
            && expressionContext != null
            && expressionContext.getWindow() != null
            && !sameWindows(window, expressionContext.getWindow())) {
            throw new ParseException("'" + name + "' uses the windows of the query's window clause, and "
                                     + "can't be given others", 0);
        }
        if (window == null) {
            throw new ParseException("'" + name + "' can only be used in a query that has a window clause, "
                                     + "as in 'window EventTime by 1d'", 0);
        }
        try {
            timeWindows = TimeWindows.create(window);
        } catch (final IllegalArgumentException e) {
            throw new ParseException(e.getMessage(), 0);
        }
        if (ownParams.length < minOwnParams || ownParams.length > maxOwnParams) {
            throw new ParseException(
                    "Invalid number of parameters supplied for '" + name + "' function: expected "
                    + (minOwnParams == maxOwnParams
                            ? minOwnParams
                            : (minOwnParams + " to " + maxOwnParams))
                    + ", got " + ownParams.length, 0);
        }
        super.setParams(ownParams);
    }

    private static String getSpec(final Param[] params) {
        if (params.length > 0 && isSpec(params[params.length - 1])) {
            return params[params.length - 1].toString().substring(SPEC_PREFIX.length());
        }
        return null;
    }

    private static boolean isSpec(final Param param) {
        return param instanceof final ValString valString
               && valString.toString() != null
               && valString.toString().startsWith(SPEC_PREFIX);
    }

    private ParseException reservedValueException() {
        return new ParseException("A value given to '" + name + "' can't start with '" + SPEC_PREFIX
                                  + "', which Stroom uses to hold the windows of the query", 0);
    }

    private HoppingWindow parseSpec(final String spec) throws ParseException {
        final String[] parts = spec.split("\\" + SPEC_SEPARATOR, -1);
        if (parts.length != 4) {
            // This is not the form that Stroom writes, so it is a value that the query gave.
            throw reservedValueException();
        }
        return new HoppingWindow(null, parts[0], emptyToNull(parts[1]), emptyToNull(parts[2]), emptyToNull(parts[3]));
    }

    private static boolean sameWindows(final HoppingWindow a, final HoppingWindow b) {
        return Objects.equals(a.getWindowSize(), b.getWindowSize())
               && Objects.equals(a.getAdvanceSize(), b.getAdvanceSize())
               && Objects.equals(a.getOffset(), b.getOffset())
               && Objects.equals(a.getTimeZone(), b.getTimeZone());
    }

    private static String emptyToNull(final String string) {
        return string.isEmpty()
                ? null
                : string;
    }

    private static String nullToEmpty(final String string) {
        return string == null
                ? ""
                : string;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(name);
        sb.append("(");
        appendParams(sb);
        if (params != null && params.length > 0) {
            sb.append(", ");
        }
        ValString.create(SPEC_PREFIX
                         + window.getWindowSize() + SPEC_SEPARATOR
                         + nullToEmpty(window.getAdvanceSize()) + SPEC_SEPARATOR
                         + nullToEmpty(window.getOffset()) + SPEC_SEPARATOR
                         + nullToEmpty(window.getTimeZone())).appendString(sb);
        sb.append(")");
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        windowKeyReference = valueReferenceIndex.addValue(name);
        valueReferenceIndex.setUsesWindows();
        super.addValueReferences(valueReferenceIndex);
    }

    // A row's window is the same for every row grouped with it, so like an aggregate its value is
    // kept for a grouped row.
    @Override
    public boolean hasAggregate() {
        return true;
    }

    /// Gets the windows of the query this function is in.
    ///
    /// @return The windows, once the function's parameters are set.
    TimeWindows getTimeWindows() {
        return timeWindows;
    }

    /// Gets where the key of a row's window is kept in its stored values.
    ///
    /// @return The reference, once the function's value references are added.
    ValReference getWindowKeyReference() {
        return windowKeyReference;
    }


    // --------------------------------------------------------------------------------


    /// Keeps the key of the window that a row is in, and gives a value worked out from it.
    abstract static class AbstractWindowGenerator extends AbstractNoChildGenerator {

        private final ValReference windowKeyReference;

        /// Creates the generator.
        ///
        /// @param windowKeyReference Where the window key is kept in a row's stored values.
        AbstractWindowGenerator(final ValReference windowKeyReference) {
            this.windowKeyReference = windowKeyReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            windowKeyReference.set(storedValues, ValLong.create(storedValues.getWindowKey()));
        }

        // A row copied from another window is grouped into this one, so has this window's key.
        @Override
        public void setShifted(final Val[] values, final StoredValues storedValues) {
            set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Long windowKey = windowKeyReference.get(storedValues).toLong();
            if (windowKey == null) {
                return ValNull.INSTANCE;
            }
            return eval(windowKey);
        }

        /// Gives the value for a row.
        ///
        /// @param windowKey The key of the window the row is in.
        /// @return The value.
        abstract Val eval(long windowKey);
    }
}
