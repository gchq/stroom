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

import stroom.index.shared.IndexFieldImpl;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.language.functions.Val;
import stroom.util.CharBuffer;
import stroom.util.shared.Severity;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFieldFilter extends AbstractXMLFilter {

    private static final String DOCUMENT = "document";
    private static final String FIELD = "field";
    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String ANALYSER = "analyser";
    private static final String INDEXED = "indexed";
    private static final String STORED = "stored";
    private static final String TERM_POSITIONS = "termPositions";
    private static final String CASE_SENSITIVE = "caseSensitive";
    private static final String VALUE = "value";

    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final CharBuffer content = new CharBuffer();

    private Locator locator;

    private final List<FieldValue> currentFieldValues = new ArrayList<>();
    private IndexFieldImpl.Builder currentFieldBuilder = IndexFieldImpl.builder();
    private Val currentVal;

    public AbstractFieldFilter(final LocationFactoryProxy locationFactory,
                               final ErrorReceiverProxy errorReceiverProxy) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        content.clear();
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (DOCUMENT.equals(localName)) {
            if (!currentFieldValues.isEmpty()) {
                processFields(currentFieldValues);
            }
            currentFieldValues.clear();
            currentFieldBuilder = IndexFieldImpl.builder();
            currentVal = null;

        } else if (FIELD.equals(localName)) {
            if (currentFieldBuilder != null && currentVal != null) {
                final IndexFieldImpl indexField = currentFieldBuilder.build();
                final FieldValue fieldValue = new FieldValue(indexField, currentVal);
                currentFieldValues.add(fieldValue);
            }

            currentFieldBuilder = IndexFieldImpl.builder();
            currentVal = null;

        } else if (NAME.equals(localName)) {
            currentFieldBuilder.fldName(content.toString());
        } else if (TYPE.equals(localName)) {
            final FieldType type = FieldType.fromDisplayValue(content.toString());
            currentFieldBuilder.fldType(type);
        } else if (ANALYSER.equals(localName)) {
            final AnalyzerType analyzerType = AnalyzerType.fromDisplayValue(content.toString());
            currentFieldBuilder.analyzerType(analyzerType);
        } else if (INDEXED.equals(localName)) {
            currentFieldBuilder.indexed(Boolean.parseBoolean(content.toString()));
        } else if (STORED.equals(localName)) {
            currentFieldBuilder.stored(Boolean.parseBoolean(content.toString()));
        } else if (TERM_POSITIONS.equals(localName)) {
            currentFieldBuilder.termPositions(Boolean.parseBoolean(content.toString()));
        } else if (CASE_SENSITIVE.equals(localName)) {
            currentFieldBuilder.caseSensitive(Boolean.parseBoolean(content.toString()));
        } else if (VALUE.equals(localName)) {
            final IndexFieldImpl indexField = currentFieldBuilder.build();
            currentVal = convertValue(indexField, content.toString());
        }

        content.clear();
        super.endElement(uri, localName, qName);
    }

    @Override
    public final void characters(final char[] ch, final int start, final int length) throws SAXException {
        content.append(ch, start, length);
        super.characters(ch, start, length);
    }

    private Val convertValue(final IndexField indexField, final String value) {
        try {
            return IndexFieldUtil.convertValue(indexField, value);
        } catch (final RuntimeException e) {
            log(Severity.ERROR, e.getMessage(), e);
        }
        return null;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }

    protected abstract void processFields(final List<FieldValue> fieldValues);
}
