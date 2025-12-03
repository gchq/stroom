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

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;

import java.util.List;

@ConfigurableElement(
        type = "DynamicSearchResultOutputFilter",
        category = Category.FILTER,
        description = """
                Used in a search extraction pipeline for extracting field values that have \
                not been stored in the index and where the fields are dynamic and derived from \
                the data rather than being defined in the Index settings.
                Consumes XML events in the `index-documents:1` namespace to convert them into a form so \
                that they can be used in a Dashboard/Query/Analytic.""",
        roles = {
                PipelineElementType.ROLE_TARGET},
        icon = SvgImage.PIPELINE_SEARCH_OUTPUT)
public class DynamicSearchResultOutputFilter extends AbstractFieldFilter {

    private final FieldListConsumerHolder fieldListConsumerHolder;

    @Inject
    public DynamicSearchResultOutputFilter(final LocationFactoryProxy locationFactory,
                                           final ErrorReceiverProxy errorReceiverProxy,
                                           final FieldListConsumerHolder fieldListConsumerHolder) {
        super(locationFactory, errorReceiverProxy);
        this.fieldListConsumerHolder = fieldListConsumerHolder;
    }

    @Override
    protected void processFields(final List<FieldValue> fieldValues) {
        fieldListConsumerHolder.acceptFieldValues(fieldValues);
    }
}
