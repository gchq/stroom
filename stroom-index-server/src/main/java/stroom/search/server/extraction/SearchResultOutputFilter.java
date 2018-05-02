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

package stroom.search.server.extraction;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.search.server.extraction.ExtractionTask.ResultReceiver;
import stroom.util.spring.StroomScope;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "SearchResultOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, icon = ElementIcons.SEARCH)
public class SearchResultOutputFilter extends AbstractXMLFilter {
    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private FieldIndexMap fieldIndexes;
    private ResultReceiver resultReceiver;
    private Val[] values;

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        if (DATA.equals(localName) && values != null) {
            String name = atts.getValue(NAME);
            String value = atts.getValue(VALUE);
            if (name != null && value != null) {
                name = name.trim();
                value = value.trim();

                if (name.length() > 0 && value.length() > 0) {
                    final int fieldIndex = fieldIndexes.get(name);
                    if (fieldIndex >= 0) {
                        values[fieldIndex] = ValString.create(value);
                    }
                }
            }
        } else if (RECORD.equals(localName)) {
            values = new Val[fieldIndexes.size()];
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (RECORD.equals(localName)) {
            resultReceiver.receive(values);
            values = null;
        }

        super.endElement(uri, localName, qName);
    }

    public void setup(final FieldIndexMap fieldIndexes, final ResultReceiver resultReceiver) {
        this.fieldIndexes = fieldIndexes;
        this.resultReceiver = resultReceiver;
    }
}
