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

package stroom.test.common.data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Produces XML like:
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <records xmlns=\"namespace\">
 *   <record>
 *     <field1>field1-value1</field1>
 *     <field2>field2-value1</field1>
 *     <field3>field3-value1</field1>
 *   </record>
 *   <record>
 *     <field1>field1-value2</field1>
 *     <field2>field2-value2</field1>
 *     <field3>field3-value2</field1>
 *   </record>
 *   </records>
 * }
 * </pre>
 */
public class XmlElementsDataWriterBuilder extends AbstractXmlDataWriterBuilder {

    public static XmlElementsDataWriterBuilder builder() {
        return new XmlElementsDataWriterBuilder();
    }

    public static DataWriter defaultXmlElementFormat() {
        return XmlElementsDataWriterBuilder.builder()
                .build();
    }

    @Override
    public XmlElementsDataWriterBuilder namespace(final String namespace) {
        super.namespace(namespace);
        return this;
    }

    @Override
    public XmlElementsDataWriterBuilder rootElementName(final String rootElementName) {
        super.rootElementName(rootElementName);
        return this;
    }

    @Override
    public XmlElementsDataWriterBuilder recordElementName(final String recordElementName) {
        super.recordElementName(recordElementName);
        return this;
    }

    protected final String buildRecordFormatString(final List<Field> fields) {
        final String fieldsPart = fields.stream()
                .map(field -> "<" + field.getName() + ">%s</" + field.getName() + ">")
                .collect(Collectors.joining());

        return "<" + recordElementName + ">" + fieldsPart + "</" + recordElementName + ">";
    }
}
