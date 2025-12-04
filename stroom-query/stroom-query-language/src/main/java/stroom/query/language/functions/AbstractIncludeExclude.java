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

abstract class AbstractIncludeExclude extends AbstractManyChildFunction {

    private Generator gen;
    private boolean simple;

    AbstractIncludeExclude(final String name) {
        super(name, 2, Integer.MAX_VALUE);
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

            boolean found = false;
            for (int i = 1; i < params.length && !found; i++) {
                final String regex = params[i].toString();
                if (regex.isEmpty()) {
                    throw new ParseException(
                            "An empty regex has been defined for argument of '" + name + "' function", 0);
                }

                final Pattern pattern = PatternCache.get(regex);
                if (pattern.matcher(value).matches()) {
                    found = true;
                }
            }

            if (inverse()) {
                found = !found;
            }

            if (found) {
                gen = new StaticValueGen(ValString.create(value));
            } else {
                gen = Null.GEN;
            }

        } else {
            for (int i = 1; i < params.length; i++) {
                if (params[i] instanceof Val) {
                    // Test regex is valid.
                    final String regex = params[i].toString();
                    if (regex.isEmpty()) {
                        throw new ParseException(
                                "An empty regex has been defined for argument of '" + name + "' function", 0);
                    }
                    PatternCache.get(regex);
                }
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
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }

    abstract boolean inverse();


    // --------------------------------------------------------------------------------


    abstract static class AbstractGen extends AbstractManyChildGenerator {

        AbstractGen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }

            try {
                final String value = val.toString();

                boolean found = false;
                for (int i = 1; i < childGenerators.length && !found; i++) {
                    final Val v = childGenerators[i].eval(storedValues, childDataSupplier);
                    if (v.type().isValue()) {
                        final String regex = v.toString();
                        if (!regex.isEmpty()) {
                            final Pattern pattern = PatternCache.get(regex);
                            if (pattern.matcher(value).matches()) {
                                found = true;
                            }
                        }
                    }
                }

                if (inverse()) {
                    found = !found;
                }

                if (found) {
                    return ValString.create(value);
                } else {
                    return ValNull.INSTANCE;
                }

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }

        abstract boolean inverse();
    }
}
