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

import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.SearchDebugUtil;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.common.v2.StringFieldValue;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

@ConfigurableElement(
        type = "SearchResultOutputFilter",
        category = Category.FILTER,
        description = """
                Used in a search extraction pipeline for extracting field values that have \
                not been stored in the index and where the field definitions are defined in the Index settings.
                Consumes XML events in the `records:2` namespace to convert them into a form so \
                that they can be used in a Dashboard/Query/Analytic.""",
        roles = {
                PipelineElementType.ROLE_TARGET},
        icon = SvgImage.PIPELINE_SEARCH_OUTPUT)
public class SearchResultOutputFilter extends AbstractXMLFilter {

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final QueryInfoHolder queryInfoHolder;
    private final FieldListConsumerHolder fieldListConsumerHolder;

    private QueryKey queryKey;
    private List<StringFieldValue> stringFieldValues;

    @Inject
    public SearchResultOutputFilter(final QueryInfoHolder queryInfoHolder,
                                    final FieldListConsumerHolder fieldListConsumerHolder) {
        this.queryInfoHolder = queryInfoHolder;
        this.fieldListConsumerHolder = fieldListConsumerHolder;
    }

    @Override
    public void startProcessing() {
        super.startProcessing();
        this.queryKey = queryInfoHolder.getQueryKey();
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (DATA.equals(localName) && stringFieldValues != null) {
            SearchProgressLog.increment(queryKey,
                    SearchPhase.SEARCH_RESULT_OUTPUT_FILTER_START_DATA);
            String name = atts.getValue(NAME);
            String value = atts.getValue(VALUE);
            if (name != null && value != null) {
                name = name.trim();
                value = value.trim();

                if (!name.isEmpty() && !value.isEmpty()) {
                    stringFieldValues.add(new StringFieldValue(name, value));
                }
            }
        } else if (RECORD.equals(localName)) {
            SearchProgressLog.increment(queryKey,
                    SearchPhase.SEARCH_RESULT_OUTPUT_FILTER_START_RECORD);
            stringFieldValues = new ArrayList<>();
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (RECORD.equals(localName)) {
            SearchProgressLog.increment(queryKey,
                    SearchPhase.SEARCH_RESULT_OUTPUT_FILTER_END_RECORD);
            SearchDebugUtil.writeExtractionData(stringFieldValues);
            fieldListConsumerHolder.acceptStringValues(stringFieldValues);
            stringFieldValues = null;
        }
    }
}
