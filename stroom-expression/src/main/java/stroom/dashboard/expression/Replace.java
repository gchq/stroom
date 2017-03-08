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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Replace extends AbstractFunction implements Serializable {
    public static final String NAME = "replace";
    private static final long serialVersionUID = -305845496003936297L;
    private String replacement;
    private SerializablePattern pattern;
    private Generator gen;
    private Function function = null;
    private boolean hasAggregate;

    public Replace(final String name) {
        super(name, 3, 3);
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        super.setParams(params);

        if (!(params[1] instanceof String)) {
            throw new ParseException("String expected as second argument of '" + name + "' function", 0);
        }
        final String regex = params[1].toString();
        if (regex.length() == 0) {
            throw new ParseException("An empty regex has been defined for second argument of '" + name + "' function", 0);
        }
        if (!(params[2] instanceof String)) {
            throw new ParseException("String expected as third argument of '" + name + "' function", 0);
        }
        replacement = params[2].toString();

        // Try and create pattern with the supplied regex.
        pattern = new SerializablePattern(regex);
        pattern.getOrCreatePattern();

        final Object param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            // Optimise replacement of static input in case user does something
            // stupid.
            final String newValue = pattern.matcher(param.toString()).replaceAll(replacement);
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
        return new Gen(childGenerator, pattern, replacement);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        private final SerializablePattern pattern;
        private final String replacement;

        public Gen(final Generator childGenerator, final SerializablePattern pattern, final String replacement) {
            super(childGenerator);
            this.pattern = pattern;
            this.replacement = replacement;
        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
        }

        @Override
        public Object eval() {
            final Object val = childGenerator.eval();
            return pattern.matcher(TypeConverter.getString(val)).replaceAll(replacement);
        }
    }

    private static class SerializablePattern implements Serializable {
        private static final long serialVersionUID = 3482210112462557773L;

        private final String regex;
        private transient volatile Pattern pattern;

        public SerializablePattern(final String regex) {
            this.regex = regex;
        }

        public Matcher matcher(final CharSequence input) {
            return getOrCreatePattern().matcher(input);
        }

        public Pattern getOrCreatePattern() {
            if (pattern == null) {
                pattern = Pattern.compile(regex);
            }
            return pattern;
        }
    }
}
