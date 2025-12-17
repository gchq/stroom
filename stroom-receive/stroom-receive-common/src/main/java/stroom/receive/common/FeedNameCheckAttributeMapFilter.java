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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Templator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the Feed attribute is supplied. If content auto creation is not enabled and
 * the Feed attribute is not provided, then a reject {@link StroomStreamException} will be thrown.
 * <p>
 * If content auto creation is enabled then the Feed name may be derived from the supplied
 * attributes if all the mandatory attributes have been provided.
 * If the mandatory attributes have not been provided then a reject {@link StroomStreamException}
 * will be thrown.
 * </p>
 */
public class FeedNameCheckAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedNameCheckAttributeMapFilter.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final CachedValue<FeedNameGenerator, ConfigState> cachedFeedNameGenerator;

    @Inject
    public FeedNameCheckAttributeMapFilter(final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.cachedFeedNameGenerator = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() -> ConfigState.fromConfig(receiveDataConfigProvider.get()))
                .withValueFunction(FeedNameGenerator::new)
                .build();
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        final String feedName = NullSafe.string(attributeMap.get(StandardHeaderArguments.FEED));

        if (feedName.isEmpty()) {
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
            if (receiveDataConfig.isFeedNameGenerationEnabled()) {
                LOGGER.debug("filter() - No feed name supplied, feedNameGenerationEnabled: true");
                final Set<String> mandatoryHeaders = receiveDataConfig.getFeedNameGenerationMandatoryHeaders();
                if (NullSafe.hasItems(mandatoryHeaders)) {
                    for (final String mandatoryHeader : mandatoryHeaders) {
                        final String mandatoryHeaderValue = attributeMap.get(mandatoryHeader);
                        if (NullSafe.isBlankString(mandatoryHeaderValue)) {
                            throw new StroomStreamException(
                                    StroomStatusCode.MISSING_MANDATORY_HEADER,
                                    attributeMap,
                                    "Mandatory header '" + mandatoryHeader + "' must be provided");
                        }
                    }
                }
                final String generatedFeedName = cachedFeedNameGenerator.getValue()
                        .generateName(attributeMap);
                // Add the generated feed name as everything else depends on the feed name
                LOGGER.debug("filter() - generatedFeedName: '{}', attributeMap: {}", generatedFeedName, attributeMap);
                attributeMap.put(StandardHeaderArguments.FEED, generatedFeedName);
            } else {
                LOGGER.debug("filter() - No feed name supplied, feedNameGenerationEnabled: false");
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
            }
        }
        return true;
    }


    // --------------------------------------------------------------------------------


    /**
     * Pkg private for testing
     */
    record ConfigState(boolean feedNameGenerationEnabled,
                       String feedNameTemplate) {

        static ConfigState fromConfig(final ReceiveDataConfig receiveDataConfig) {
            return new ConfigState(
                    receiveDataConfig.isFeedNameGenerationEnabled(),
                    receiveDataConfig.getFeedNameTemplate());
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Pkg private for testing
     */
    static class FeedNameGenerator {

        /**
         * Finds unwanted chars in a param
         */
        private static final Pattern PARAM_REPLACEMENT_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");
        /**
         * Finds unwanted chars in the static text
         */
        private static final Pattern STATIC_REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");

        private static final String FEED_ONLY_TEMPLATE = "${feed}";

        private final Templator templator;

        public FeedNameGenerator(final ConfigState configState) {
            if (configState.feedNameGenerationEnabled) {
                try {
                    this.templator = TemplateUtil.parseTemplate(
                            configState.feedNameTemplate,
                            FeedNameGenerator::normaliseParamReplacement,
                            FeedNameGenerator::normaliseStaticText);
                } catch (final Exception e) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Error parsing feed name template '{}': {}",
                            configState.feedNameTemplate, LogUtil.exceptionMessage(e)));
                }
            } else {
                // Feed name gen not enabled so just get the feed name from the attr map
                this.templator = TemplateUtil.parseTemplate(
                        FEED_ONLY_TEMPLATE,
                        str -> NullSafe.string(str).toUpperCase());
            }
        }

        public String generateName(final AttributeMap attributeMap) {
            return templator.generateWith(attributeMap);
        }

        private static String normaliseParamReplacement(final String name) {
            String result = NullSafe.trim(name);
            result = result.toUpperCase();
            result = PARAM_REPLACEMENT_REPLACE_PATTERN.matcher(result)
                    .replaceAll("_");
            return result;
        }

        private static String normaliseStaticText(final String name) {
            String result = NullSafe.trim(name);
            result = result.toUpperCase();
            result = STATIC_REPLACE_PATTERN.matcher(result)
                    .replaceAll("_");
            return result;
        }
    }
}
