package stroom.util.test.data;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlatDataWriterBuilder {
    private boolean isHeaderIncluded = true;
    private String delimiter = ",";
    private Optional<String> optEnclosingChars = Optional.empty();

    public static FlatDataWriterBuilder builder() {
        return new FlatDataWriterBuilder();
    }

    public static DataWriter defaultCsvFormat() {
        return FlatDataWriterBuilder.builder()
                        .outputHeaderRow(true)
                        .delimitedBy(",")
                        .build();
    }

    public FlatDataWriterBuilder outputHeaderRow(final boolean isHeaderIncluded) {
        this.isHeaderIncluded = isHeaderIncluded;
        return this;
    }

    public FlatDataWriterBuilder delimitedBy(final String delimiter) {
        //TODO need to consider escaping instance of the delimiter in values,
        this.delimiter = delimiter;
        return this;
    }

    public FlatDataWriterBuilder enclosedBy(final String enclosingChars) {
        //TODO need to consider escaping instance of the delimiter in values,
        this.optEnclosingChars = Optional.of(enclosingChars);
        return this;
    }

    private Function<Record, String> getDataMapper() {
        final Function<String, String> enclosureMapper = getEnclosureMapper();

        return record ->
                record.getValues().stream()
                        .map(enclosureMapper)
                        .collect(Collectors.joining(delimiter));
    }

    public DataWriter build() {
        //return our mapping function which conforms to the DataWriter interface
        return this::mapRecords;
    }

    private Stream<String> mapRecords(List<Field> fieldDefinitions, Stream<Record> recordStream) {
        Function<Record, String> dataMapper = getDataMapper();

        Stream<String> dataStream = recordStream.map(dataMapper);
        if (isHeaderIncluded) {
            return Stream.concat(generateHeaderRow(fieldDefinitions), dataStream);
        } else {
            return dataStream;
        }
    }

    private Stream<String> generateHeaderRow(final List<Field> fieldDefinitions) {

        final Function<String, String> enclosureMapper = getEnclosureMapper();
        String header = fieldDefinitions.stream()
                .map(Field::getName)
                .map(enclosureMapper)
                .collect(Collectors.joining(delimiter));
        return Stream.of(header);
    }

    private Function<String, String> getEnclosureMapper() {
        return optEnclosingChars.map(chars ->
                (Function<String, String>) str ->
                        (chars + str + chars))
                .orElse(Function.identity());
    }
}
