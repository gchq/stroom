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

abstract class AbstractLink extends AbstractManyChildFunction {

    AbstractLink(final String name, final int minParams, final int maxParams) {
        super(name, minParams, maxParams);
    }

    abstract static class AbstractLinkGen extends AbstractManyChildGenerator {

        AbstractLinkGen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        Val makeLink(final Val text, final Val url, final Val type) {
            if (text.type().isError()) {
                return text;
            }
            if (url.type().isError()) {
                return url;
            }
            if (type.type().isError()) {
                return type;
            }

            return makeLink(getEscapedString(text), getEscapedString(url), getEscapedString(type));
        }

        Val makeLink(final String text, final String url, final String type) {
            return makeLink(text, url, type, null);
        }

        Val makeLink(final String text, final String url, final String type, final String target) {
            final StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(text);
            sb.append("](");
            sb.append(url);
            sb.append(")");
            if (type != null && !type.isEmpty()) {
                sb.append("{");
                sb.append(type);
                sb.append("}");
            }
            if (target != null && !target.isEmpty()) {
                sb.append("<").append(target).append(">");
            }

            return ValString.create(sb.toString());
        }

        String getEscapedString(final Val val) {
            if (val.type().isValue()) {
                return EncodingUtil.encodeUrl(val.toString());
            }
            return "";
        }
    }
}
