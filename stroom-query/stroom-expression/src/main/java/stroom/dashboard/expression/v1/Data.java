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

package stroom.dashboard.expression.v1;

class Data extends AbstractLink {
    static final String NAME = "data";

    public Data(final String name) {
        super(name, 2, 8);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    private static final class Gen extends AbstractLinkGen {
        private static final long serialVersionUID = 217968020285584214L;

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
            final StringBuilder sb = new StringBuilder();

            append(sb, 1, "id");
            append(sb, 2, "partNo");
            append(sb, 3, "recordNo");
            append(sb, 4, "lineFrom");
            append(sb, 5, "colFrom");
            append(sb, 6, "lineTo");
            append(sb, 7, "colTo");

            return makeLink(getEscapedString(childGenerators[0].eval()), EncodingUtil.encodeUrl(sb.toString()), "data");
        }

        private void append(final StringBuilder sb, final int index, final String key) {
            if (index < childGenerators.length) {
                final Val val = childGenerators[index].eval();
                if (val.type().isValue()) {
                    if (sb.length() > 0) {
                        sb.append("&");
                    } else {
                        sb.append("?");
                    }
                    sb.append(key);
                    sb.append("=");
                    sb.append(getEscapedString(val));
                }
            }
        }
    }
}
