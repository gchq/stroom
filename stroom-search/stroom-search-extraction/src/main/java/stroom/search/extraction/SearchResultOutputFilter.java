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
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.Values;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.SearchDebugUtil;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.inject.Inject;

@ConfigurableElement(type = "SearchResultOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET}, icon = ElementIcons.SEARCH)
public class SearchResultOutputFilter extends AbstractXMLFilter {

    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final ExtractionStateHolder extractionStateHolder;

    private QueryKey queryKey;
    private FieldIndex fieldIndex;
    private Val[] values;

    @Inject
    public SearchResultOutputFilter(final ExtractionStateHolder extractionStateHolder) {
        this.extractionStateHolder = extractionStateHolder;
    }

    @Override
    public void startProcessing() {
        super.startProcessing();
        this.queryKey = extractionStateHolder.getQueryKey();
        this.fieldIndex = extractionStateHolder.getFieldIndex();
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (DATA.equals(localName) && values != null) {
            SearchProgressLog.increment(queryKey,
                    SearchPhase.SEARCH_RESULT_OUTPUT_FILTER_START_DATA);
            String name = atts.getValue(NAME);
            String value = atts.getValue(VALUE);
            if (name != null && value != null) {
                name = name.trim();
                value = value.trim();

                if (name.length() > 0 && value.length() > 0) {
                    final Integer pos = fieldIndex.getPos(name);
                    if (pos != null) {
                        values[pos] = ValString.create(value);
                    }
                }
            }
        } else if (RECORD.equals(localName)) {
            SearchProgressLog.increment(queryKey,
                    SearchPhase.SEARCH_RESULT_OUTPUT_FILTER_START_RECORD);
            values = new Val[fieldIndex.size()];
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (RECORD.equals(localName)) {
            SearchProgressLog.increment(queryKey,
                    SearchPhase.SEARCH_RESULT_OUTPUT_FILTER_END_RECORD);
            SearchDebugUtil.writeExtractionData(values);
            extractionStateHolder.getReceiver().add(Values.of(values));
            extractionStateHolder.incrementCount();
            values = null;
        }
    }
}
