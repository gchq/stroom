/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression;

import java.io.Serializable;
import java.text.ParseException;

public class Decode extends AbstractFunction implements Serializable {
    public static final String NAME = "decode";
    private static final long serialVersionUID = -305845496003936297L;
    private Generator gen;
    private Function function = null;
    private boolean hasAggregate;
    private String[] str;
    private SerializablePattern[] test;
    private String[] result;
    private String otherwise;

    public Decode(final String name) {
        super(name, 4, Integer.MAX_VALUE);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        if (params.length % 2 == 1) {
            throw new ParseException("Expected to get an even number of arguments of '" + name + "' function", 0);
        }
        test = new SerializablePattern[params.length / 2];
        result = new String[params.length / 2];
        str = new String[params.length - 1];
        int j = 0, k = 0;

        for (int i = 1; i < params.length - 1; i++) {
            if (i % 2 == 1) {
                final String regex = TypeConverter.getString(params[i]);
                if (regex.length() == 0) {
                    throw new ParseException("An empty regex has been defined in '" + name + "' function", 0);
                }
                test[j] = new SerializablePattern(regex);
            } else {
                result[j] = (String) params[i];
                j++;
            }
            str[k++] = (String) params[i];
        }
        otherwise = (String) params[params.length - 1];
        str[str.length - 1] = otherwise;

        final Object param = params[0];
        String newValue = otherwise;

        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            /*
             * Optimise replacement of static input in case user does something
			 * stupid.
			 */
            for (int i = 0; i < test.length; i++) {
                if (test[i].matcher(TypeConverter.getString(param)).matches()) {
                    newValue = result[i];
                    break;
                }
            }
            gen = new StaticValueFunction(newValue).createGenerator();
            hasAggregate = false;
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, str);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        private final String[] str;

        public Gen(final Generator childGenerator, final String... str) {
            super(childGenerator);
            this.str = str;
        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
        }

        @Override
        public Object eval() {
            final Object val = childGenerator.eval();

            final SerializablePattern[] test = new SerializablePattern[str.length / 2];
            final String[] result = new String[str.length / 2];

            int j = 0;
            for (int i = 0; i < str.length - 1; i++) {
                if (i % 2 == 0) {
                    test[j] = new SerializablePattern(str[i]);
                } else {
                    result[j] = str[i];
                    j++;
                }
            }

            String newValue = str[str.length - 1];
            for (int i = 0; i < test.length; i++) {
                if (test[i].matcher(TypeConverter.getString(val)).matches()) {
                    newValue = result[i];
                    break;
                }
            }

            return newValue;
        }
    }
}
