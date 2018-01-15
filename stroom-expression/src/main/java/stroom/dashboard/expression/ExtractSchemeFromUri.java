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

package stroom.dashboard.expression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class ExtractSchemeFromUri extends ExtractionFunction {
    public static class ExtractorImpl implements Extractor {
        private static final long serialVersionUID = -5893918049538006730L;

        private static final Logger LOGGER = LoggerFactory.getLogger(ExtractorImpl.class);

        @Override
        public String extract(final String value) {
            try {
                final URI uri = new URI(value);
                return uri.getScheme();
            } catch (final Exception e) {
                LOGGER.debug(e.getMessage(), e);
            }
            return null;
        }
    }

    public static final String NAME = "extractSchemeFromUri";
    private static final Extractor EXTRACTOR = new ExtractorImpl();

    public ExtractSchemeFromUri(final String name) {
        super(name);
    }

    @Override
    Extractor getExtractor() {
        return EXTRACTOR;
    }
}
