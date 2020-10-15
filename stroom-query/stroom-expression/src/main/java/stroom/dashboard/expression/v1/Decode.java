/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.expression.v1;

import java.io.Serializable;
import java.text.ParseException;
import java.util.regex.Pattern;

class Decode extends AbstractManyChildFunction implements Serializable {
    static final String NAME = "decode";
    private static final long serialVersionUID = -305845496003936297L;
    private Generator gen;
    private boolean simple;

    public Decode(final String name) {
        super(name, 4, Integer.MAX_VALUE);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length % 2 == 1) {
            throw new ParseException("Expected to get an even number of arguments of '" + name + "' function", 0);
        }

        // See if this is a static computation.
        simple = true;
        for (Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation.
            final String value = params[0].toString();

            String newValue = params[params.length - 1].toString();
            for (int i = 1; i < params.length - 1; i += 2) {
                final String regex = params[i].toString();
                if (regex.length() == 0) {
                    throw new ParseException("An empty regex has been defined for argument of '" + name + "' function", 0);
                }

                final Pattern pattern = PatternCache.get(regex);
                if (pattern.matcher(value).matches()) {
                    newValue = params[i + 1].toString();
                    break;
                }
            }

            gen = new StaticValueFunction(ValString.create(newValue)).createGenerator();

        } else {
            for (int i = 1; i < params.length - 1; i += 2) {
                if (params[i] instanceof Val) {
                    // Test regex is valid.
                    final String regex = params[i].toString();
                    if (regex.length() == 0) {
                        throw new ParseException("An empty regex has been defined for argument of '" + name + "' function", 0);
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

    private static class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public void set(final Val[] values) {
            for (final Generator generator : childGenerators) {
                generator.set(values);
            }
        }

        @Override
        public Val eval() {
            final Val val = childGenerators[0].eval();
            if (!val.type().isValue()) {
                return val;
            }

            try {
                final String value = val.toString();
                Val newVal = childGenerators[childGenerators.length - 1].eval();
                if (!newVal.type().isValue()) {
                    return ValErr.wrap(newVal);
                }
                String newValue = newVal.toString();

                for (int i = 1; i < childGenerators.length - 1; i += 2) {
                    final Val valRegex = childGenerators[i].eval();
                    if (!valRegex.type().isValue()) {
                        return ValErr.wrap(valRegex);
                    }

                    final String regex = valRegex.toString();
                    if (regex.length() == 0) {
                        return ValErr.create("Empty regex");
                    }

                    final Pattern pattern = PatternCache.get(regex);
                    if (pattern.matcher(value).matches()) {
                        newVal = childGenerators[i + 1].eval();
                        if (!newVal.type().isValue()) {
                            return ValErr.wrap(newVal);
                        }
                        newValue = newVal.toString();
                        break;
                    }
                }

                if (newValue == null) {
                    return ValNull.INSTANCE;
                }

                return ValString.create(newValue);

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
