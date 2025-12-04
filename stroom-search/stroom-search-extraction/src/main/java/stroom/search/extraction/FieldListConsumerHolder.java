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

package stroom.search.extraction;

import stroom.query.common.v2.StringFieldValue;
import stroom.util.pipeline.scope.PipelineScoped;

import jakarta.inject.Inject;

import java.util.List;

@PipelineScoped
public class FieldListConsumerHolder implements FieldListConsumer {

    private final ExtractionState extractionState;
    private FieldListConsumer fieldListConsumer;

    @Inject
    FieldListConsumerHolder(final ExtractionState extractionState) {
        this.extractionState = extractionState;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        fieldListConsumer.acceptFieldValues(fieldValues);
        extractionState.incrementCount();
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        fieldListConsumer.acceptStringValues(stringValues);
        extractionState.incrementCount();
    }

    public void setFieldListConsumer(final FieldListConsumer fieldListConsumer) {
        this.fieldListConsumer = fieldListConsumer;
    }
}
