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
        name = Replace.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "",
        signatures = @FunctionSignature(
                description = "Perform text replacement on an input string using a regular expression to match part " +
                        "(or all) of the input string and a replacement string to insert in place of all matches.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The string to search using the regex pattern.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "pattern",
                                description = "The regex pattern to match all or part of the input string.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "replacement",
                                description = "The string to replace each match with.",
                                argType = ValString.class)}))
class Replace extends AbstractManyChildFunction {

    static final String NAME = "replace";
    private Generator gen;
    private boolean simple;

    public Replace(final String name) {
        super(name, 3, 3);
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
            final String replacement = params[2].toString();

            if (regex.length() == 0) {
                throw new ParseException(
                        "An empty regex has been defined for second argument of '" + name + "' function", 0);
            }

            final Pattern pattern = PatternCache.get(regex);
            final String newValue = pattern.matcher(value).replaceAll(replacement);
            gen = new StaticValueFunction(ValString.create(newValue)).createGenerator();

        } else {
            if (params[1] instanceof Val) {
                // Test regex is valid.
                final String regex = params[1].toString();
                if (regex.length() == 0) {
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


    // --------------------------------------------------------------------------------


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
            final Val valReplacement = childGenerators[2].eval(storedValues, childDataSupplier);
            if (!valReplacement.type().isValue()) {
                return ValErr.wrap(valReplacement);
            }

            try {
                final String value = val.toString();
                final String regex = valRegex.toString();
                final String replacement = valReplacement.toString();
                final Pattern pattern = PatternCache.get(regex);
                return ValString.create(pattern.matcher(value).replaceAll(replacement));

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
