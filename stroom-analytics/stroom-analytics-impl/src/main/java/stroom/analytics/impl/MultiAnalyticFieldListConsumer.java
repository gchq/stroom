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

import stroom.query.common.v2.StringFieldValue;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;

import java.util.List;

public class MultiAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final List<AnalyticFieldListConsumer> consumers;

    public MultiAnalyticFieldListConsumer(final List<AnalyticFieldListConsumer> consumers) {
        this.consumers = consumers;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.acceptFieldValues(fieldValues);
        }
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.acceptStringValues(stringValues);
        }
    }

    @Override
    public void start() {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.start();
        }
    }

    @Override
    public void end() {
        for (final AnalyticFieldListConsumer consumer : consumers) {
            consumer.end();
        }
    }
}
