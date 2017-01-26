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

public class Concat extends AbstractManyChildFunction {
    public static final String NAME = "concat";

    public Concat(final String name) {
        super(name, 2, Integer.MAX_VALUE);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    private static class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = 217968020285584214L;

        public Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public void set(final String[] values) {
            for (final Generator generator : childGenerators) {
                generator.set(values);
            }
        }

        @Override
        public Object eval() {
            final StringBuilder sb = new StringBuilder();
            for (final Generator gen : childGenerators) {
                final Object value = gen.eval();
                if (value != null) {
                    sb.append(TypeConverter.getString(value));
                }
            }
            return sb.toString();
        }
    }
}
