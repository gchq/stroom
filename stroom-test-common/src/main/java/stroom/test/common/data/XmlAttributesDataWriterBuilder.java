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
 *     <data name="field1" value="field1-value1"/>
 *     <data name="field2" value="field2-value1"/>
 *     <data name="field3" value="field3-value1"/>
 *   </record>
 *   <record>
 *     <data name="field1" value="field1-value2"/>
 *     <data name="field2" value="field2-value2"/>
 *     <data name="field3" value="field3-value2"/>
 *   </record>
 *   </records>
 * }
 * </pre>
 */
public class XmlAttributesDataWriterBuilder extends AbstractXmlDataWriterBuilder {

    private String fieldValueElementName = "data";

    public static XmlAttributesDataWriterBuilder builder() {
        return new XmlAttributesDataWriterBuilder();
    }

    public static DataWriter defaultXmlElementFormat() {
        return XmlAttributesDataWriterBuilder.builder()
                .build();
    }

    @Override
    public XmlAttributesDataWriterBuilder namespace(final String namespace) {
        super.namespace(namespace);
        return this;
    }

    @Override
    public XmlAttributesDataWriterBuilder rootElementName(final String rootElementName) {
        super.rootElementName(rootElementName);
        return this;
    }

    @Override
    public XmlAttributesDataWriterBuilder recordElementName(final String recordElementName) {
        super.recordElementName(recordElementName);
        return this;
    }

    public XmlAttributesDataWriterBuilder fieldValueElementName(final String fieldValueElementName) {
        this.fieldValueElementName = fieldValueElementName;
        return this;
    }

    @Override
    protected final String buildRecordFormatString(final List<Field> fields) {
        final String fieldsPart = fields.stream()
                .map(field -> "<" + fieldValueElementName + " name=\"" + field.getName() + "\" value=\"%s\" />")
                .collect(Collectors.joining());

        return "<" + recordElementName + ">"
                + fieldsPart
                + "</" + recordElementName + ">";
    }

}
