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

import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Match.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "True if pattern matches input",
        signatures = @FunctionSignature(
                description = "Tests an input string using a regular expression pattern.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The string to test using the regex pattern.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "pattern",
                                description = "The regex pattern to test with.",
                                argType = ValString.class)}))
class Match extends AbstractManyChildFunction {

    static final String NAME = "match";
    private Generator gen;
    private boolean simple;

    public Match(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // See if this is a static computation.
        simple = true;
        for (final Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation.
            final String value = params[0].toString();
            final String regex = params[1].toString();

            if (regex.isEmpty()) {
                throw new ParseException(
                        "An empty regex has been defined for second argument of '" + name + "' function", 0);
            }

            final Pattern pattern = PatternCache.get(regex);
            final boolean matches = pattern.matcher(value).matches();
            gen = new StaticValueFunction(ValBoolean.create(matches)).createGenerator();

        } else {
            if (params[1] instanceof Val) {
                // Test regex is valid.
                final String regex = params[1].toString();
                if (regex.isEmpty()) {
                    throw new ParseException(
                            "An empty regex has been defined for second argument of '" + name + "' function", 0);
                }
                PatternCache.get(regex);
            }
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }
        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }

    private static final class Gen extends AbstractManyChildGenerator {

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }
            final Val valRegex = childGenerators[1].eval(storedValues, childDataSupplier);
            if (!valRegex.type().isValue()) {
                return ValErr.wrap(valRegex);
            }

            try {
                final String value = val.toString();
                final String regex = valRegex.toString();
                final Pattern pattern = PatternCache.get(regex);
                return ValBoolean.create(pattern.matcher(value).matches());

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
