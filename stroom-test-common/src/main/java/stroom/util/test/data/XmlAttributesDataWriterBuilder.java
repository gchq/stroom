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
