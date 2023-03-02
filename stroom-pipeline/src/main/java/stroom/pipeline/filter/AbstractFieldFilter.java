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

package stroom.pipeline.filter;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValBoolean;
import stroom.dashboard.expression.v1.ValDate;
import stroom.dashboard.expression.v1.ValDouble;
import stroom.dashboard.expression.v1.ValFloat;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.index.shared.AnalyzerType;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class AbstractFieldFilter extends AbstractXMLFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFieldFilter.class);

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

    private Locator locator;


    private IndexField.Builder currentFieldBuilder;
    private String currentElement;
    private String currentValue;

    private List<FieldValue> currentFieldValues;

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
        currentElement = localName;
        if (DOCUMENT.equals(localName)) {
            currentFieldValues = new ArrayList<>();
        } else if (FIELD.equals(localName)) {
            currentFieldBuilder = IndexField.builder();
            currentValue = null;
        }
        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (DOCUMENT.equals(localName)) {
            if (currentFieldValues.size() > 0) {
                processFields(currentFieldValues);
            }
            currentFieldValues = null;
            currentFieldBuilder = null;
            currentValue = null;
        } else if (FIELD.equals(localName)) {
            final IndexField indexField = currentFieldBuilder.build();
            final Val val = convertValue(indexField, currentValue);
            if (val != null) {
                final FieldValue fieldValue = new FieldValue(indexField, val);
                currentFieldValues.add(fieldValue);
            }
        }

        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        final String string = new String(ch, start, length);
        if (NAME.equals(currentElement)) {
            currentFieldBuilder.fieldName(string);
        } else if (TYPE.equals(currentElement)) {
            final IndexFieldType indexFieldType = IndexFieldType.TYPE_MAP.get(string.toLowerCase(Locale.ROOT));
            currentFieldBuilder.fieldType(indexFieldType);
        } else if (ANALYSER.equals(currentElement)) {
            final AnalyzerType analyzerType = AnalyzerType.TYPE_MAP.get(string.toLowerCase(Locale.ROOT));
            currentFieldBuilder.analyzerType(analyzerType);
        } else if (INDEXED.equals(currentElement)) {
            currentFieldBuilder.indexed(Boolean.parseBoolean(string));
        } else if (STORED.equals(currentElement)) {
            currentFieldBuilder.stored(Boolean.parseBoolean(string));
        } else if (TERM_POSITIONS.equals(currentElement)) {
            currentFieldBuilder.termPositions(Boolean.parseBoolean(string));
        } else if (CASE_SENSITIVE.equals(currentElement)) {
            currentFieldBuilder.caseSensitive(Boolean.parseBoolean(string));
        } else if (VALUE.equals(currentElement)) {
            currentValue = string;
        }

        super.characters(ch, start, length);
    }

    private Val convertValue(final IndexField indexField, final String value) {
        try {
            switch (indexField.getFieldType()) {
                case LONG_FIELD, NUMERIC_FIELD, ID -> {
                    final long val = Long.parseLong(value);
                    return ValLong.create(val);
                }
                case BOOLEAN_FIELD -> {
                    final boolean val = Boolean.parseBoolean(value);
                    return ValBoolean.create(val);
                }
                case INTEGER_FIELD -> {
                    final int val = Integer.parseInt(value);
                    return ValInteger.create(val);
                }
                case FLOAT_FIELD -> {
                    final float val = Float.parseFloat(value);
                    return ValFloat.create(val);
                }
                case DOUBLE_FIELD -> {
                    final double val = Double.parseDouble(value);
                    return ValDouble.create(val);
                }
                case DATE_FIELD -> {
                    final long val = DateUtil.parseNormalDateTimeString(value);
                    return ValDate.create(val);
                }
                case FIELD -> {
                    return ValString.create(value);
                }
            }
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
