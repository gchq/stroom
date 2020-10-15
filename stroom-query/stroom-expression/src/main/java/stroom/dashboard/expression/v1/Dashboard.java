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

class Dashboard extends AbstractLink {
    static final String NAME = "dashboard";

    public Dashboard(final String name) {
        super(name, 2, 3);
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
            Val link = ValNull.INSTANCE;

            if (childGenerators.length == 2) {
                final Val text = childGenerators[0].eval();
                final Val uuid = childGenerators[1].eval();
                link = makeDashboardLink(text, uuid, ValNull.INSTANCE);
            } else if (childGenerators.length == 3) {
                final Val text = childGenerators[0].eval();
                final Val uuid = childGenerators[1].eval();
                final Val params = childGenerators[2].eval();
                link = makeDashboardLink(text, uuid, params);
            }

            return link;
        }

        private Val makeDashboardLink(final Val text, final Val uuid, final Val params) {
            if (text.type().isError()) {
                return text;
            }
            if (uuid.type().isError()) {
                return uuid;
            }
            if (params.type().isError()) {
                return params;
            }

            final StringBuilder url = new StringBuilder();
            url.append("?uuid=");
            url.append(getEscapedString(uuid));
            if (params.type().isValue()) {
                url.append("&params=");
                url.append(getEscapedString(params));
            }

            return makeLink(getEscapedString(text), EncodingUtil.encodeUrl(url.toString()), "dashboard");
        }
    }
}
