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

class Link extends AbstractLink {
    static final String NAME = "link";

    public Link(final String name) {
        super(name, 1, 3);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new LinkGen(childGenerators);
    }

    private static final class LinkGen extends AbstractLinkGen {
        private static final long serialVersionUID = 217968020285584214L;

        LinkGen(final Generator[] childGenerators) {
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

            if (childGenerators.length == 1) {
                final Val url = childGenerators[0].eval();
                link = makeLink(url, url, ValNull.INSTANCE);
            } else if (childGenerators.length == 2) {
                final Val text = childGenerators[0].eval();
                final Val url = childGenerators[1].eval();
                link = makeLink(text, url, ValNull.INSTANCE);
            } else if (childGenerators.length == 3) {
                final Val text = childGenerators[0].eval();
                final Val url = childGenerators[1].eval();
                final Val type = childGenerators[2].eval();
                link = makeLink(text, url, type);
            }

            return link;
        }
    }
}
