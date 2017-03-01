/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.expression;

public class Ref extends AbstractFunction {
    private static final NullGen NULL_GEN = new NullGen();
    private final String text;
    private final int fieldIndex;

    public Ref(final String text, final int fieldIndex) {
        super(text, 0, 0);
        this.text = text;
        this.fieldIndex = fieldIndex;
    }

    @Override
    public Generator createGenerator() {
        // If the field index is less than 0 then we will always return null so
        // get the null generator.
        if (fieldIndex < 0) {
            return NULL_GEN;
        } else {
            return new Gen(fieldIndex);
        }
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(text);
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    private static class NullGen extends AbstractNoChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        @Override
        public void set(final String[] values) {
        }

        @Override
        public Object eval() {
            return null;
        }
    }

    private static class Gen extends AbstractNoChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

        private final int fieldIndex;
        private String current;

        public Gen(final int fieldIndex) {
            this.fieldIndex = fieldIndex;
        }

        @Override
        public void set(final String[] values) {
            current = values[fieldIndex];
        }

        @Override
        public Object eval() {
            return current;
        }
    }
}
