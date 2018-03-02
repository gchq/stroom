package stroom.util.test.data;

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
