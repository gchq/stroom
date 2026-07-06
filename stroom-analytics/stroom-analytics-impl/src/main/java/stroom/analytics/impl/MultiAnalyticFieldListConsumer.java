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

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.query.common.v2.StringFieldValue;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;

import java.util.List;

public class MultiAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final List<Entry> entries;
    private final AnalyticRuleHolder analyticRuleHolder;

    public MultiAnalyticFieldListConsumer(final List<Entry> entries,
                                          final AnalyticRuleHolder analyticRuleHolder) {
        this.entries = entries;
        this.analyticRuleHolder = analyticRuleHolder;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        for (final Entry entry : entries) {
            analyticRuleHolder.setAnalyticRuleDoc(entry.analyticRuleDoc);
            try {
                entry.consumer.acceptFieldValues(fieldValues);
            } finally {
                analyticRuleHolder.clear();
            }
        }
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        for (final Entry entry : entries) {
            analyticRuleHolder.setAnalyticRuleDoc(entry.analyticRuleDoc);
            try {
                entry.consumer.acceptStringValues(stringValues);
            } finally {
                analyticRuleHolder.clear();
            }
        }
    }

    @Override
    public void start() {
        for (final Entry entry : entries) {
            analyticRuleHolder.setAnalyticRuleDoc(entry.analyticRuleDoc);
            try {
                entry.consumer.start();
            } finally {
                analyticRuleHolder.clear();
            }
        }
    }

    @Override
    public void end() {
        for (final Entry entry : entries) {
            analyticRuleHolder.setAnalyticRuleDoc(entry.analyticRuleDoc);
            try {
                entry.consumer.end();
            } finally {
                analyticRuleHolder.clear();
            }
        }
    }

    /**
     * Pairs an analytic rule doc with its field list consumer.
     */
    public record Entry(AbstractAnalyticRuleDoc analyticRuleDoc, AnalyticFieldListConsumer consumer) {

    }
}
