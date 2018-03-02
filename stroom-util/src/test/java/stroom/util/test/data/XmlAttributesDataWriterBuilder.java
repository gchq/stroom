package stroom.util.test.data;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * </pre>
 */
public class XmlAttributesDataWriterBuilder {

    private Optional<String> namespace = Optional.empty();
    private String rootElementName = "records";
    private String recordElementName = "record";
    private String fieldValueElementName = "data";

    public static XmlAttributesDataWriterBuilder builder() {
        return new XmlAttributesDataWriterBuilder();
    }

    public static DataWriter defaultXmlElementFormat() {
        return XmlAttributesDataWriterBuilder.builder()
                .build();
    }

    public XmlAttributesDataWriterBuilder namespace(final String namespace) {
        this.namespace = Optional.of(namespace);
        return this;
    }

    public XmlAttributesDataWriterBuilder rootElementName(final String rootElementName) {
        this.rootElementName = rootElementName;
        return this;
    }

    public XmlAttributesDataWriterBuilder recordElementName(final String recordElementName) {
        this.recordElementName = recordElementName;
        return this;
    }

    public XmlAttributesDataWriterBuilder fieldValueElementName(final String fielValueElementName) {
        this.fieldValueElementName = fielValueElementName;
        return this;
    }

    public DataWriter build() {
        //return our mapping function which conforms to the DataWriter interface
        return this::mapRecords;
    }

    private Function<Record, String> getDataMapper(final List<Field> fields) {
        final String recordFormatStr = buildRecordFormatString(fields);

        return record -> {
            String[] valuesArr = new String[record.getValues().size()];
            record.getValues().toArray(valuesArr);
            return String.format(recordFormatStr, (Object[]) valuesArr);
        };
    }

    private final String buildRecordFormatString(final List<Field> fields) {
        final String fieldsPart = fields.stream()
                .map(field -> "<" + fieldValueElementName + " name=\"" + field.getName() + "\" value=\"%s\" />")
                .collect(Collectors.joining());

        return "<" + recordElementName + ">"
                + fieldsPart
                + "</" + recordElementName + ">";
    }

    private Stream<String> mapRecords(List<Field> fields, Stream<Record> recordStream) {
        final Function<Record, String> dataMapper = getDataMapper(fields);

        final Stream<String> dataStream = recordStream.map(dataMapper);
        final String namespaceAtr = namespace
                .map(namespace -> String.format(" xmlns=\"%s\"", namespace))
                .orElse("");

        final String xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        final String openRootElm = String.format("<%s%s>", rootElementName, namespaceAtr);
        final String closeRootElm = String.format("</%s>", rootElementName);

        final Stream<String> headerStream = Stream.of(xmlDeclaration, openRootElm);
        final Stream<String> footerStream = Stream.of(closeRootElm);
        return Stream.concat(Stream.concat(headerStream, dataStream), footerStream);
    }
}
