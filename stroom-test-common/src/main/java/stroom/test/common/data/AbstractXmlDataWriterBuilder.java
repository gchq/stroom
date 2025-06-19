package stroom.test.common.data;

import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class AbstractXmlDataWriterBuilder {

    private String namespace = null;
    private String rootElementName = "records";
    String recordElementName = "record";

    public AbstractXmlDataWriterBuilder namespace(final String namespace) {
        this.namespace = namespace;
        return this;
    }

    public AbstractXmlDataWriterBuilder rootElementName(final String rootElementName) {
        this.rootElementName = rootElementName;
        return this;
    }

    public AbstractXmlDataWriterBuilder recordElementName(final String recordElementName) {
        this.recordElementName = recordElementName;
        return this;
    }

    public DataWriter build() {
        //return our mapping function which conforms to the DataWriter interface
        return (fieldDefinitions, recordStream) ->
                mapRecords(namespace, rootElementName, fieldDefinitions, recordStream);
    }

    private Function<DataRecord, String> getDataMapper(final List<Field> fields) {
        final String recordFormatStr = buildRecordFormatString(fields);

        return dataRecord -> {
            final String[] valuesArr = new String[dataRecord.values().size()];
            dataRecord.values().toArray(valuesArr);
            return String.format(recordFormatStr, (Object[]) valuesArr);
        };
    }

    protected abstract String buildRecordFormatString(List<Field> fields);

    Stream<String> mapRecords(final String namespace,
                              final String rootElementName,
                              final List<Field> fields,
                              final Stream<DataRecord> recordStream) {
        final Function<DataRecord, String> dataMapper = getDataMapper(fields);

        final Stream<String> dataStream = recordStream.map(dataMapper);
        final String namespaceAtr = NullSafe.getOrElse(
                namespace,
                namespace2 -> String.format(" xmlns=\"%s\"", namespace2),
                "");

        final String xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        final String openRootElm = String.format("<%s%s>", rootElementName, namespaceAtr);
        final String closeRootElm = String.format("</%s>", rootElementName);

        final Stream<String> headerStream = Stream.of(xmlDeclaration, openRootElm);
        final Stream<String> footerStream = Stream.of(closeRootElm);

        //have to force stream to sequential to ensure header and footer go at the
        //top and bottom respectively.
        //TODO it would probably be better to improve the DataWrite interface to expose methods
        //to get the header/footer, then the data stream can be done in parallel
        return Stream.concat(Stream.concat(headerStream, dataStream), footerStream).sequential();
    }
}
