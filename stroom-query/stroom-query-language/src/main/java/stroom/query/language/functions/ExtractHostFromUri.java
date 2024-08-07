/*
 * Copyright 2018 Crown Copyright
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ExtractHostFromUri.NAME,
        commonCategory = FunctionCategory.URI,
        commonReturnType = ValString.class,
        commonReturnDescription = "The host from the URI or null if not found or the URI is mall-formed. e.g. " +
                ExtractHostFromUri.NAME + "('http://foo:bar@w1.superman.com:8080/very/long/path.html?" +
                "p1=v1&p2=v2#more-details') returns 'w1.superman.com'.",
        signatures = @FunctionSignature(
                description = "Extract the host component from a URI.",
                args = {
                        @FunctionArg(
                                name = "uri",
                                description = "The URI to extract the host from.",
                                argType = ValString.class)
                }))
class ExtractHostFromUri extends ExtractionFunction {

    static final String NAME = "extractHostFromUri";
    private static final Extractor EXTRACTOR = new ExtractorImpl();

    public ExtractHostFromUri(final String name) {
        super(name);
    }

    @Override
    Extractor getExtractor() {
        return EXTRACTOR;
    }

    static class ExtractorImpl implements Extractor {

        private static final Logger LOGGER = LoggerFactory.getLogger(ExtractorImpl.class);

        @Override
        public String extract(final String value) {
            try {
                final URI uri = new URI(value);
                return uri.getHost();
            } catch (final URISyntaxException | RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }
            return null;
        }
    }
}
