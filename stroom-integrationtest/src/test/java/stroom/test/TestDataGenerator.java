package stroom.test;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestDataGenerator {

    public static Builder buildDefinition() {
        return new Builder();
    }


    public static Consumer<Stream<String>> systemOutConsumer() {
        return stringStream -> stringStream.forEach(System.out::println);
    }


//    private TestDataGenerator(final List<TestDataFieldDefinition> fieldDefinitions,
//                              final Consumer<Stream<String>> rowStreamConsumer,
//                              final int rowCount,
//                              final boolean isHeaderIncluded) {
//
//
//    }

    public static class Builder {
        private List<TestDataFieldDefinition> fieldDefinitions = new ArrayList<>();
        private Consumer<Stream<String>> rowStreamConsumer;
        private int rowCount = 1;
        private boolean isHeaderIncluded = true;
        private String delimiter = ",";
        private Optional<String> optEnclosingChars = Optional.empty();

//        private Builder(final List<TestDataFieldDefinition> fieldDefinitions,
//                        final Consumer<Stream<String>> rowStreamConsumer,
//                        final int rowCount,
//                        final boolean isHeaderIncluded,
//                        final String delimiter) {
//
//            this.fieldDefinitions = fieldDefinitions;
//            this.rowStreamConsumer = rowStreamConsumer;
//            this.rowCount = rowCount;
//            this.isHeaderIncluded = isHeaderIncluded;
//            this.delimiter = delimiter;
//        }

        public Builder addFieldDefinition(final TestDataFieldDefinition fieldDefinition) {
            fieldDefinitions.add(Preconditions.checkNotNull(fieldDefinition));
            return this;
        }

        public Builder consumedBy(Consumer<Stream<String>> rowStreamConsumer) {
            this.rowStreamConsumer = Preconditions.checkNotNull(rowStreamConsumer);
            return this;
        }

        public Builder rowCount(final int rowCount) {
            Preconditions.checkArgument(rowCount > 0, "rowCount must be > 0");
            this.rowCount = rowCount;
            return this;
        }

        public Builder outputHeaderRow(final boolean isHeaderIncluded) {
            this.isHeaderIncluded = isHeaderIncluded;
            return this;
        }

        public Builder delimitedBy(final String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder enclosedBy(final String enclosingChars) {
            this.optEnclosingChars = Optional.of(enclosingChars);
            return this;
        }

        public void generate() {
            if (fieldDefinitions.isEmpty()) {
                throw new RuntimeException("No field definitions defined");
            }
            if (rowStreamConsumer == null) {
                throw new RuntimeException("No consumer defined");
            }

            Stream<String> rowStream;
            if (isHeaderIncluded) {
                rowStreamConsumer.accept(Stream.concat(generateHeaderRow(), generateDataRows()));
            } else {
                rowStreamConsumer.accept(generateDataRows());
            }
        }

        private Function<String, String> getEnclosureMapper() {
            return optEnclosingChars.map(chars ->
                    (Function<String, String>) str ->
                            (chars + str + chars))
                    .orElse(Function.identity());
        }

        private Stream<String> generateHeaderRow() {

            final Function<String, String> enclosureMapper = getEnclosureMapper();
            String header = fieldDefinitions.stream()
                    .map(TestDataFieldDefinition::getName)
                    .map(enclosureMapper)
                    .collect(Collectors.joining(delimiter));
            return Stream.of(header);
        }

        private Stream<String> generateDataRows() {

            final Function<String, String> enclosureMapper = getEnclosureMapper();
            Function<Integer, String> mapper = integer ->
                    fieldDefinitions.stream()
                            .map(TestDataFieldDefinition::getNext)
                            .map(enclosureMapper)
                            .collect(Collectors.joining(delimiter));

            return IntStream.rangeClosed(0, rowCount)
                    .sequential()
                    .boxed()
                    .map(mapper);
        }

    }
}
