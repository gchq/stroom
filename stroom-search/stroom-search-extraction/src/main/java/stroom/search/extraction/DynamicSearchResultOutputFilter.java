/*
 * Copyright 2016 Crown Copyright
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

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.Values;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.AbstractFieldFilter;
import stroom.pipeline.filter.FieldValue;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;

import java.util.List;
import javax.inject.Inject;

@ConfigurableElement(type = "DynamicSearchResultOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET}, icon = ElementIcons.SEARCH)
public class DynamicSearchResultOutputFilter extends AbstractFieldFilter {

    private final ExtractionStateHolder extractionStateHolder;
    private FieldIndex fieldIndex;

    @Inject
    public DynamicSearchResultOutputFilter(final LocationFactoryProxy locationFactory,
                                           final ErrorReceiverProxy errorReceiverProxy,
                                           final ExtractionStateHolder extractionStateHolder) {
        super(locationFactory, errorReceiverProxy);
        this.extractionStateHolder = extractionStateHolder;
    }

    @Override
    public void startProcessing() {
        super.startProcessing();
        this.fieldIndex = extractionStateHolder.getFieldIndex();
    }

    @Override
    protected void processFields(final List<FieldValue> fieldValues) {
        if (extractionStateHolder.getReceiver() != null) {
            final Val[] values = new Val[fieldIndex.size()];
            for (final FieldValue fieldValue : fieldValues) {
                final Integer pos = fieldIndex.getPos(fieldValue.field().getFieldName());
                if (pos != null) {
                    values[pos] = fieldValue.value();
                }
            }
            extractionStateHolder.getReceiver().add(Values.of(values));
        }
        if (extractionStateHolder.getFieldListConsumer() != null) {
            extractionStateHolder.getFieldListConsumer().accept(fieldValues);
        }
        extractionStateHolder.incrementCount();
    }
}
