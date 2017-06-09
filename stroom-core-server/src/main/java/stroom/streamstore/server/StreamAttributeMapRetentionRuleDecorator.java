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

package stroom.streamstore.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.shared.DictionaryService;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.date.DateUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamAttributeMapRetentionRuleDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapRetentionRuleDecorator.class);

    private final List<DataRetentionRule> rules;
    private final ExpressionMatcher expressionMatcher;

    public StreamAttributeMapRetentionRuleDecorator(final DictionaryService dictionaryService, final List<DataRetentionRule> rules) {
        this.rules = rules;
        expressionMatcher = new ExpressionMatcher(StreamFields.getFieldMap(), dictionaryService);
    }

    public void addMatchingRetentionRuleInfo(final StreamAttributeMap streamAttributeMap) {
        int index = -1;

        // If there are no active rules then we aren't going to process anything.
        if (rules.size() > 0) {
            // Create an attribute map we can match on.
            final Map<String, Object> attributeMap = createAttributeMap(streamAttributeMap);
            index = findMatchingRuleIndex(attributeMap);
        }

        if (index != -1) {
            final DataRetentionRule rule = rules.get(index);
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_AGE, rule.getAgeString());

            String keepUntil = DataRetentionRule.FOREVER;
            if (streamAttributeMap.getStream() != null) {
                final long millis = streamAttributeMap.getStream().getCreateMs();
                final LocalDateTime createTime = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime();
                final Long ms = DataRetentionAgeUtil.plus(createTime, rule);
                if (ms != null) {
                    keepUntil = DateUtil.createNormalDateTimeString(ms);
                }
            }

            // Create a rule name that includes the rule number.
            String ruleName;
            if (rule.getName() != null && rule.getName().length() > 0) {
                ruleName = (index + 1) + " " + rule.getName();
            } else {
                ruleName = String.valueOf(index + 1);
            }

            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_UNTIL, keepUntil);
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_RULE, ruleName);
        } else {
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_AGE, DataRetentionRule.FOREVER);
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_UNTIL, DataRetentionRule.FOREVER);
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_RULE, "None");
        }
    }

    private int findMatchingRuleIndex(final Map<String, Object> attributeMap) {
        for (int i = 0; i < rules.size(); i++) {
            final DataRetentionRule rule = rules.get(i);
            // We will ignore rules that are not enabled or have no enabled expression.
            if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().isEnabled()) {
                if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                    return i;
                }
            }
        }

        return -1;
    }

    private Map<String, Object> createAttributeMap(final StreamAttributeMap streamAttributeMap) {
        final Map<String, Object> attributeMap = new HashMap<>();

        final Stream stream = streamAttributeMap.getStream();
        if (stream != null) {
            attributeMap.put(StreamFields.STREAM_ID, stream.getId());
            attributeMap.put(StreamFields.CREATED_ON, stream.getCreateMs());
            if (stream.getParentStreamId() != null) {
                attributeMap.put(StreamFields.PARENT_STREAM_ID, stream.getParentStreamId());
            }
            if (stream.getStreamType() != null) {
                attributeMap.put(StreamFields.STREAM_TYPE, stream.getStreamType().getDisplayValue());
            }
            final Feed feed = stream.getFeed();
            if (feed != null) {
                attributeMap.put(StreamFields.FEED, feed.getName());
            }
            final StreamProcessor streamProcessor = stream.getStreamProcessor();
            if (streamProcessor != null) {
                final PipelineEntity pipeline = streamProcessor.getPipeline();
                if (pipeline != null) {
                    attributeMap.put(StreamFields.PIPELINE, pipeline.getName());
                }
            }
        }

        StreamFields.getFields().getIndexFields().forEach(indexField -> {
            final String value = streamAttributeMap.getAttributeValue(indexField.getFieldName());
            if (value != null) {
                try {
                    switch (indexField.getFieldType()) {
                        case FIELD:
                            attributeMap.put(indexField.getFieldName(), value);
                            break;
                        case DATE_FIELD:
                            attributeMap.put(indexField.getFieldName(), DateUtil.parseNormalDateTimeString(value));
                            break;
                        default:
                            attributeMap.put(indexField.getFieldName(), Long.valueOf(value));
                            break;
                    }
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        });
        return attributeMap;
    }
}
