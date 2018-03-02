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
 * </pre>
 */
public class XmlElementsDataWriterBuilder {

    private Optional<String> namespace = Optional.empty();
    private String rootElementName = "records";
    private String recordElementName = "record";

    public static XmlElementsDataWriterBuilder builder() {
        return new XmlElementsDataWriterBuilder();
    }

    public static DataWriter defaultXmlElementFormat() {
        return XmlElementsDataWriterBuilder.builder()
                .build();
    }

    public XmlElementsDataWriterBuilder namespace(final String namespace) {
        this.namespace = Optional.of(namespace);
        return this;
    }

    public XmlElementsDataWriterBuilder rootElementName(final String rootElementName) {
        this.rootElementName = rootElementName;
        return this;
    }

    public XmlElementsDataWriterBuilder recordElementName(final String recordElementName) {
        this.recordElementName = recordElementName;
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
                .map(field -> "<" + field.getName() + ">%s</" + field.getName() + ">")
                .collect(Collectors.joining());

        return "<" + recordElementName + ">" + fieldsPart + "</" + recordElementName + ">";
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
