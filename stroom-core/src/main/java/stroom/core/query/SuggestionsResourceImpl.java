/*
 * Copyright 2017 Crown Copyright
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

package stroom.core.query;

import com.codahale.metrics.health.HealthCheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.SuggestionsResource;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class SuggestionsResourceImpl implements SuggestionsResource, HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionsResourceImpl.class);
    private static final int LIMIT = 20;

    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[_\\-]");

    @Inject
    SuggestionsResourceImpl(final MetaService metaService,
                            final PipelineStore pipelineStore,
                            final SecurityContext securityContext) {
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
    }

    @Override
    public List<String> fetch(final FetchSuggestionsRequest request) {
        return securityContext.secureResult(() -> {
            List<String> result = Collections.emptyList();

            if (request.getDataSource() != null) {
                if (MetaFields.STREAM_STORE_DOC_REF.equals(request.getDataSource())) {
                    if (request.getField().getName().equals(MetaFields.FEED_NAME.getName())) {
                        result = createFeedList(request.getText());

                    } else if (request.getField().getName().equals(MetaFields.PIPELINE.getName())) {
                        result = pipelineStore.list().stream()
                                .map(DocRef::getName)
                                .filter(name -> request.getText() == null || name.contains(request.getText()))
                                .sorted()
                                .limit(LIMIT)
                                .collect(Collectors.toList());

                    } else if (request.getField().getName().equals(MetaFields.TYPE_NAME.getName())) {
                        result = createStreamTypeList(request.getText());

                    } else if (request.getField().getName().equals(MetaFields.STATUS.getName())) {
                        result = Arrays.stream(Status.values())
                                .map(Status::getDisplayValue)
                                .filter(name -> request.getText() == null || name.contains(request.getText()))
                                .sorted()
                                .limit(LIMIT)
                                .collect(Collectors.toList());
                    }
                }
            }

            return result;
        });
    }

    private List<String> createFeedList(final String input) {
        // TODO this seems pretty inefficient as each call hits the db to get ALL feeds
        //   then limits/filters in java.  Needs to work off a cached feed name list

        // TODO consider using isFuzzyMatch below
        return metaService.getFeeds()
                .parallelStream()
                .filter(feedName -> feedName == null || input == null || feedName.startsWith(input))
                .sorted(Comparator.naturalOrder())
                .limit(LIMIT)
                .collect(Collectors.toList());
    }

    private List<String> createStreamTypeList(final String input) {
        // TODO this seems pretty inefficient as each call hits the db to get ALL feeds
        //   then limits/filters in java.  Needs to work off a cached feed name list

        // TODO consider using isFuzzyMatch below
        return metaService.getTypes()
                .parallelStream()
                .filter(typeName -> typeName == null || input == null || typeName.startsWith(input))
                .sorted(Comparator.naturalOrder())
                .limit(LIMIT)
                .collect(Collectors.toList());
    }

    /**
     * See tests for an idea of how this is meant to work. This is unfinished and
     * needs optimising.
     */
    static boolean isFuzzyMatch(final String input, final String text) {
        final String inputStr = Objects.requireNonNullElse(input, "");
        final String testStr = Objects.requireNonNullElse(text, "");

        LOGGER.debug("Testing input: {} against text: {}", inputStr, testStr);
        if (testStr.startsWith(inputStr)) {
            LOGGER.info("Matched on startsWith");
            return true;
        } else if (testStr.contains(inputStr)) {
            LOGGER.info("Matched on contains");
            return true;
        } else {
            boolean hasMatched = false;
            String patternStr = inputStr.chars().boxed()
                    .map(charVal -> {
                        char theChar = (char) charVal.intValue();
                        if (Character.isLetterOrDigit(theChar)) {
                            return String.valueOf(theChar);
                        } else if (SEPARATOR_PATTERN.matcher(String.valueOf(theChar)).matches()) {
                            return "[^_-]*" + theChar;
                        } else {
                            throw new RuntimeException("Unexpected char " + theChar);
                        }
                    })
                    .collect(Collectors.joining());
            patternStr = ".*" + patternStr + ".*";

            if (Pattern.matches(patternStr, testStr)) {
                LOGGER.info("Matched on pattern: {}", patternStr);
                hasMatched = true;
            } else {
                LOGGER.info("Not Matched on pattern: {}", patternStr);
            }

            patternStr = inputStr.chars().boxed()
                    .map(charVal -> {
                        char theChar = (char) charVal.intValue();
                        return String.valueOf(theChar);
                    })
                    .collect(Collectors.joining(".*"));
            patternStr = ".*" + patternStr + ".*";

            if (Pattern.matches(patternStr, testStr)) {
                LOGGER.info("Matched on pattern: {}", patternStr);
                hasMatched = true;
            } else {
                LOGGER.info("Not Matched on pattern: {}", patternStr);
            }
            return hasMatched;
        }
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}