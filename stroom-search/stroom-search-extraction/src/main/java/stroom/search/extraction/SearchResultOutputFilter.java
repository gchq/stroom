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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.search.coprocessor.Values;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurableElement(type = "SearchResultOutputFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET}, icon = ElementIcons.SEARCH)
public class SearchResultOutputFilter extends AbstractSearchResultOutputFilter {
    private static final String RECORD = "record";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String VALUE = "value";

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

            if (alertTableDefinitions != null) {

                if (values == null || values.length == 0) {
                    System.out.println("No values to extract from " );
                    return;
                }

                System.out.println("Going to do some extraction alert stuff " + alertTableDefinitions);

                Values vals = new Values (values);
                for (TableSettings rule : alertTableDefinitions){
                    String [] outputFields = extractAlert(rule, vals);
                    if (outputFields != null){
                        System.out.println ("Reporting an alert with vals" +
                                Arrays.stream(outputFields).collect(Collectors.joining(", ")));
                    }
                }

            } else {
                //Non-alert creation search extraction
                consumer.accept(new Values(values));
                values = null;
            }
        }

        super.endElement(uri, localName, qName);
    }


    //todo use the timezone in the dashboard?
    private final static String DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH = "UTC";

    private String[] extractAlert (TableSettings rule, Values vals) {
        final List<Field> fields = rule.getFields();
//
//        FieldIndexMap fieldIndexMap = FieldIndexMap.forFields
//                (doc.getFields().stream().map(f -> f.name()).
//                        collect(Collectors.toList()).toArray(new String[doc.getFields().size()]));
        final FieldFormatter fieldFormatter = new FieldFormatter(new FormatterFactory(DATE_TIME_LOCALE_SHOULD_BE_FROM_SEARCH));
        //See CoprocessorsFactory for creation of field Index Map
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndexes, paramMapForAlerting);


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
}
