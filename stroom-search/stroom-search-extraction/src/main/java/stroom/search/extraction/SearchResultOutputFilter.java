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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.impl.TableSettingsUtil;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
//import stroom.pipeline.xml.event.simple.AttributesImpl;
//import stroom.query.api.v2.Field;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.ExtractionDecoratorFactory.AlertDefinition;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurableElement(type = "SearchResultOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET}, icon = ElementIcons.SEARCH)
public class SearchResultOutputFilter extends AbstractSearchResultOutputFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchResultOutputFilter.class);

    private static final String RECORDS = "records";
    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;

    private Locator locator;

    private Val[] values;
    private String nsUri = null;

    @Inject
    SearchResultOutputFilter (final LocationFactoryProxy locationFactory, final ErrorReceiverProxy errorReceiverProxy){
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    public boolean isConfiguredForAlerting(){
        return alertDefinitions != null;
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        //Hold values for later use
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

        if (isConfiguredForAlerting()) {
            if (nsUri == null) {
                nsUri = uri;
            }
            if (!nsUri.equals(uri)){
                throw new IllegalStateException("Unable to process alerts from multiple XML namespaces," +
                        " changed from " + nsUri + " to " + uri);
            }
        }

        if (!isConfiguredForAlerting() || RECORDS.equals(localName)){
            super.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (RECORD.equals(localName)) {

            if (isConfiguredForAlerting()) {
                //Alert generation search extraction - create records when filters match
                if (values == null || values.length == 0) {
                    log(Severity.WARNING,"No values to extract from ",null );
                    return;
                }

                Values vals = new Values (values);
                for (AlertDefinition rule : alertDefinitions){
                    TableSettings tableSettings = TableSettingsUtil.mapTableSettings(rule.getTableComponentSettings());
                    String [] outputFields = extractAlert(tableSettings, vals);
                    if (outputFields != null){
                        writeRecord (rule, outputFields);
                        LOGGER.debug ("Reporting an alert with vals" +
                                Arrays.stream(outputFields).collect(Collectors.joining(", ")));
                    }
                }

            } else {
                //Standard (typically dashboard populating) search extraction, pass onto consumers (e.g. dashboards)
                consumer.accept(new Values(values));
                values = null;
            }
        }

        if (!isConfiguredForAlerting() || RECORDS.equals(localName)){
            super.endElement(uri, localName, qName);
        }
    }

    private void createDataElement(String name, String value) throws SAXException{
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", NAME, NAME, "xs:string", name);
        attrs.addAttribute("", VALUE, VALUE, "xs:string", value);
        super.startElement(nsUri, DATA, DATA, attrs);
        super.endElement(nsUri, DATA, DATA);
    }

    private void writeRecord(AlertDefinition alertDefinition, String[] fieldVals) throws SAXException {
        if (fieldVals == null || fieldVals.length == 0) {
            return;
        }

        LOGGER.debug("Creating an alert following filtering");
        super.startElement(nsUri, RECORD, RECORD, new AttributesImpl());

        for (String attrName : alertDefinition.getAttributes().keySet()){
            createDataElement (attrName, alertDefinition.getAttributes().get(attrName));
        }

        for (stroom.dashboard.shared.Field field: alertDefinition.getTableComponentSettings().getFields()){
            if (field.isVisible()) {
                String fieldName = field.getName();
                String fieldVal = fieldVals[fieldIndexes.get(fieldName)];
                if (fieldVal != null) {
                    createDataElement(field.getDisplayValue(), fieldVal);
                }
            }
        }
        super.endElement(nsUri, RECORD, RECORD);
    }

    //todo use the timezone in the dashboard?
    private final static String DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH = "UTC";

    private String[] extractAlert (TableSettings rule, Values vals) {
        final List<stroom.query.api.v2.Field> fields = rule.getFields();
//
//        FieldIndexMap fieldIndexMap = FieldIndexMap.forFields
//                (doc.getFields().stream().map(f -> f.name()).
//                        collect(Collectors.toList()).toArray(new String[doc.getFields().size()]));
        final FieldFormatter fieldFormatter = new FieldFormatter(new FormatterFactory(DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH));
        //See CoprocessorsFactory for creation of field Index Map
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndexes,
                paramMapForAlerting);

//See also ItemMapper:addItem()
        final String[] output = new String [vals.getValues().length];
        int index = 0;

        for (final CompiledField compiledField : compiledFields) {
            final Expression expression = compiledField.getExpression();

            if (expression != null) {
                if (expression.hasAggregate()) {
                    throw new IllegalArgumentException("Aggregate functions not supported for dashboards in rules");
                } else {
                    final Generator generator = expression.createGenerator();

                    generator.set(vals.getValues());
                    Val value = generator.eval();
                    output[index] = fieldFormatter.format(compiledField.getField(), value); //From TableResultCreator

                    if (compiledField.getCompiledFilter() != null) {
                        // If we are filtering then we need to evaluate this field
                        // now so that we can filter the resultant value.

                        if (compiledField.getCompiledFilter() != null && value != null && !compiledField.getCompiledFilter().match(value.toString())) {
                            // We want to exclude this item.
                            return null;
                        }
                    }
                }
            }

            index++;
        }

        return output;
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

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }
}
