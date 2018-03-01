package stroom.test;

import com.google.common.base.Preconditions;
import com.google.common.reflect.ClassPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestDataGenerator {

    public static DefinitionBuilder buildDefinition() {
        return new DefinitionBuilder();
    }


    public static Consumer<Stream<String>> systemOutConsumer() {
        return stringStream -> stringStream.forEach(System.out::println);
    }

    public static Consumer<Stream<String>> fileOutputConsumer(final Path filePath) {

        Preconditions.checkNotNull(filePath);

        return stringStream -> {
            try {
                Files.write(filePath, (Iterable<String>) stringStream::iterator);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Error writing to file %s",
                        filePath.toAbsolutePath().toString()), e);
            }
        };
    }

    /**
     * Allows you to wrap a {@link Field} object with xml tags, e.g.
     * <fieldName>fieldValue</fieldName>
     *
     * @param name         The name of the field
     * @param wrappedField The {@link Field} object to wrap in xml tags
     */
    public static Field asXmlTag(final String name,
                                 final Field wrappedField) {
        return new Field(name, () ->
                String.format("<%s>%s</%s>", name, wrappedField.getNext(), name));
    }

    /**
     * Stateful value supplier that supplies a value from values in sequential order
     * looping back to the beginning when it gets to the end.
     */
    public static Field sequentialValueField(final String name, final List<String> values) {
        Preconditions.checkNotNull(values);
        Preconditions.checkArgument(!values.isEmpty());
        final AtomicInteger lastIndex = new AtomicInteger(-1);
        final Supplier<String> supplier = () -> {
            if (lastIndex.incrementAndGet() >= values.size()) {
                //cycle back to first item
                lastIndex.set(0);
            }
            return values.get(lastIndex.get());
        };
        return new Field(name, supplier);
    }

    /**
     * {@link Field} that supplies a random value from values on each call to getNext()
     */
    public static Field randomValueField(final String name, final List<String> values) {
        Preconditions.checkNotNull(values);
        Preconditions.checkArgument(!values.isEmpty());
        final Random random = new Random();
        final Supplier<String> supplier = () ->
                values.get(random.nextInt(values.size()));
        return new Field(name, supplier);
    }

    /**
     * @param name         Field name for use in the header
     * @param format       A {@link String:format} compatible format containing a single
     *                     placeholder, e.g. "user-%s" or "user-%03d"
     * @param maxNumberExc A random number between 0 (inclusive) and maxNumberExc (exclusive) will
     *                     replace the %s in the format string
     * @return A complete {@link Field}
     */
    public static Field randomNumberedValueField(final String name,
                                                 final String format,
                                                 final int maxNumberExc) {
        Preconditions.checkNotNull(format);
        Preconditions.checkArgument(maxNumberExc > 0);

        final Random random = new Random();
        final Supplier<String> supplier = () ->
                String.format(format, random.nextInt(maxNumberExc));
        return new Field(name, supplier);
    }

    public static Field sequentialNumberField(final String name,
                                              final int startInc,
                                              final int endExc) {

        Preconditions.checkArgument(endExc > startInc);

        final AtomicInteger lastIndex = new AtomicInteger(-1);

        final Supplier<String> supplier = () -> {
            if (lastIndex.get() == -1L) {
                lastIndex.set(startInc);
            } else {
                if (lastIndex.incrementAndGet() >= endExc) {
                    lastIndex.set(startInc);
                }
            }
            return Integer.toString(lastIndex.get());
        };
        return new Field(name, supplier);
    }

    private static IntSupplier buildRandomNumberSupplier(final int startInc,
                                                         final int endExc) {
        Preconditions.checkArgument(endExc > startInc);

        final Random random = new Random();
        final int delta = endExc - startInc;

        return () ->
                random.nextInt(delta) + startInc;
    }

    public static Field randomNumberField(final String name,
                                          final int startInc,
                                          final int endExc) {

        Preconditions.checkArgument(endExc > startInc);

        return new Field(
                name,
                () -> Integer.toString(buildRandomNumberSupplier(startInc, endExc).getAsInt()));
    }

    public static Field randomIpV4Field(final String name) {

        final IntSupplier intSupplier = buildRandomNumberSupplier(0, 256);

        final Supplier<String> supplier = () ->
                String.format("%d.%d.%d.%d",
                        intSupplier.getAsInt(),
                        intSupplier.getAsInt(),
                        intSupplier.getAsInt(),
                        intSupplier.getAsInt());
        return new Field(name, supplier);
    }

    public static Field randomDateTime(final String name,
                                       final LocalDateTime startDateInc,
                                       final LocalDateTime endDateExc,
                                       final DateTimeFormatter formatter) {
        Preconditions.checkArgument(endDateExc.isAfter(startDateInc));
        long millisBetween = endDateExc.toInstant(ZoneOffset.UTC).toEpochMilli()
                - startDateInc.toInstant(ZoneOffset.UTC).toEpochMilli();

        final Supplier<String> supplier = () -> {
            try {
                long randomDelta = (long) (Math.random() * millisBetween);
                LocalDateTime dateTime = startDateInc.plus(randomDelta, ChronoUnit.MILLIS);

                //                System.out.println(millisBetween + " " +
                //                        randomDelta + " " +
                //                        Duration.ofMillis(randomDelta) + " " +
                //                        dateTime);

                return dateTime.format(formatter);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Time range is too large, maximum allowed: %s",
                        Duration.ofMillis(Integer.MAX_VALUE).toString()));
            }
        };
        return new Field(name, supplier);
    }

    public static Field sequentialDateTime(final String name,
                                           final LocalDateTime startDateInc,
                                           final Duration delta,
                                           final DateTimeFormatter formatter) {

        final Supplier<String> supplier = () -> {
            try {
                return startDateInc.plus(delta).format(formatter);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Time range is too large, maximum allowed: %s",
                        Duration.ofMillis(Integer.MAX_VALUE).toString()));
            }
        };
        return new Field(name, supplier);
    }

    public static Field uuid(final String name) {
        return new Field(name, () -> UUID.randomUUID().toString());
    }

    /**
     * A field populated with a random number (between minCount and maxCount) of
     * words separated by ' '. The words are picked at random from all the class
     * names in the 'stroom' package on the classpath
     */
    public static Field randomClassNames(final String name,
                                         final int minCount,
                                         final int maxCount) {
        Preconditions.checkArgument(minCount >= 0);
        Preconditions.checkArgument(maxCount >= minCount);

        final Random random = new Random();
        final List<String> classNames = ClassNamesListHolder.getClassNames();

        return randomWords(name, minCount, maxCount, classNames);
    }

    /**
     * A field populated with a random number (between minCount and maxCount) of
     * words separated by ' ' as picked randomly from wordList
     */
    public static Field randomWords(final String name,
                                    final int minCount,
                                    final int maxCount,
                                    final List<String> wordList) {
        Preconditions.checkArgument(minCount >= 0);
        Preconditions.checkArgument(maxCount >= minCount);

        final Random random = new Random();
        //        System.out.println("ClassNames size: " + wordList.size());

        Supplier<String> supplier = () -> {
            int wordCount = random.nextInt(maxCount - minCount + 1) + minCount;
            return IntStream.rangeClosed(0, wordCount)
                    .boxed()
                    .map(i -> wordList.get(random.nextInt(wordList.size())))
                    .collect(Collectors.joining(" "))
                    .replaceAll("(^\\s+|\\s+$)", "") //remove leading/trailing spaces
                    .replaceAll("\\s\\s+", " "); //replace multiple spaces with one
        };

        return new Field(name, supplier);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class ClassNamesListHolder {
        private static List<String> classNames;

        static {
            //lazy initialisation
            classNames = generateList();
        }

        public static List<String> getClassNames() {
            return classNames;
        }

        private static List<String> generateList() {

            final ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try {
                return ClassPath.from(loader).getAllClasses().stream()
                        .filter(classInfo -> classInfo.getPackageName().startsWith("stroom."))
                        .map(ClassPath.ClassInfo::getSimpleName)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException("Error reading classloader", e);
            }
        }
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface DataWriter {
        Stream<String> mapRecords(final List<Field> fieldDefinitions,
                                  final Stream<Record> recordStream);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static class FlatDataWriterBuilder {
        private boolean isHeaderIncluded = true;
        private String delimiter = ",";
        private Optional<String> optEnclosingChars = Optional.empty();

        public static FlatDataWriterBuilder builder() {
            return new FlatDataWriterBuilder();
        }

        public FlatDataWriterBuilder outputHeaderRow(final boolean isHeaderIncluded) {
            this.isHeaderIncluded = isHeaderIncluded;
            return this;
        }

        public FlatDataWriterBuilder delimitedBy(final String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public FlatDataWriterBuilder enclosedBy(final String enclosingChars) {
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
            return this::mapRecords;
        }

        public Stream<String> mapRecords(List<Field> fieldDefinitions, Stream<Record> recordStream) {
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


    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

//    public static class XmlDataWriterBuilder {
//
//        public static XmlDataWriterBuilder builder() {
//            return new XmlDataWriterBuilder();
//        }
//
//
//        private Function<Record, String> getDataMapper() {
//            final Function<String, String> enclosureMapper = getEnclosureMapper();
//
//            return record ->
//                    record.getValues().stream()
//                            .map(enclosureMapper)
//                            .collect(Collectors.joining(delimiter));
//        }
//
//        public DataWriter build() {
//            return this::mapRecords;
//        }
//
//        public Stream<String> mapRecords(List<Field> fieldDefinitions, Stream<Record> recordStream) {
//            Function<Record, String> dataMapper = getDataMapper();
//
//            Stream<String> dataStream = recordStream.map(dataMapper);
//            if (isHeaderIncluded) {
//                return Stream.concat(generateHeaderRow(fieldDefinitions), dataStream);
//            } else {
//                return dataStream;
//            }
//        }
//
//        private Stream<String> generateHeaderRow(final List<Field> fieldDefinitions) {
//
//            final Function<String, String> enclosureMapper = getEnclosureMapper();
//            String header = fieldDefinitions.stream()
//                    .map(Field::getName)
//                    .map(enclosureMapper)
//                    .collect(Collectors.joining(delimiter));
//            return Stream.of(header);
//        }
//
//        private Function<String, String> getEnclosureMapper() {
//            return optEnclosingChars.map(chars ->
//                    (Function<String, String>) str ->
//                            (chars + str + chars))
//                    .orElse(Function.identity());
//        }
//
//    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static class DefinitionBuilder {
        private List<Field> fieldDefinitions = new ArrayList<>();
        private Consumer<Stream<String>> rowStreamConsumer;
        private int rowCount = 1;
        private DataWriter dataWriter;

        public DefinitionBuilder addFieldDefinition(final Field fieldDefinition) {
            boolean isNamedAlreadyUsed = fieldDefinitions.stream()
                    .map(Field::getName)
                    .anyMatch(Predicate.isEqual(fieldDefinition.getName()));
            Preconditions.checkArgument(!isNamedAlreadyUsed, "Name is already in use");

            fieldDefinitions.add(Preconditions.checkNotNull(fieldDefinition));
            return this;
        }

        public DefinitionBuilder consumedBy(final Consumer<Stream<String>> rowStreamConsumer) {
            this.rowStreamConsumer = Preconditions.checkNotNull(rowStreamConsumer);
            return this;
        }

        public DefinitionBuilder setDataWriter(final DataWriter dataWriter) {
            this.dataWriter = Preconditions.checkNotNull(dataWriter);
            return this;
        }

        public DefinitionBuilder rowCount(final int rowCount) {
            Preconditions.checkArgument(rowCount > 0, "rowCount must be > 0");
            this.rowCount = rowCount;
            return this;
        }

        public void generate() {
            if (fieldDefinitions.isEmpty()) {
                throw new RuntimeException("No field definitions defined");
            }
            if (rowStreamConsumer == null) {
                throw new RuntimeException("No consumer defined");
            }
            if (dataWriter == null) {
                dataWriter = FlatDataWriterBuilder.builder()
                        .outputHeaderRow(true)
                        .delimitedBy(",")
                        .build();

            }

//            if (isHeaderIncluded) {
//                rowStreamConsumer.accept(Stream.concat(generateHeaderRow(), generateDataRows()));
//            } else {

//            }

            //convert our stream of data records into a stream of strings that possibly
            //includes adding things like header/footer rows, tags, delimiters, etc.
            final Stream<String> rowStream = dataWriter.mapRecords(fieldDefinitions, generateDataRows());
            rowStreamConsumer.accept(rowStream);
        }


        private Stream<Record> generateDataRows() {

            Function<Integer, Record> toRecordMapper = integer -> {
                List<String> values = fieldDefinitions.stream()
                        .map(Field::getNext)
                        .collect(Collectors.toList());
                return new Record(fieldDefinitions, values);
            };

            return IntStream.rangeClosed(0, rowCount)
                    .sequential()
                    .boxed()
                    .map(toRecordMapper);
        }

    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Class to hold the definition of a field in a set of flat test data records.
     * Multiple static factory methods exist for creating various pre-canned types
     * of test data field, e.g. a random IP address
     */
    public static class Field {

        private static final Logger LOGGER = LoggerFactory.getLogger(Field.class);

        private final String name;
        private final Supplier<String> valueFunction;

        /**
         * @param name          The name of the field
         * @param valueSupplier A supplier of values for the field
         */
        public Field(final String name,
                     final Supplier<String> valueSupplier) {

            this.name = Preconditions.checkNotNull(name);
            this.valueFunction = Preconditions.checkNotNull(valueSupplier);
        }

        /**
         * @return The next value for this field from the value supplier.
         * The value supplier may either be stateful, i.e. the next value is
         * dependant on values that cam before it or stateless, i.e. the next
         * value has no relation to previous values.
         */
        public String getNext() {
            return valueFunction.get();
        }

        /**
         * @return The name of the field
         */
        public String getName() {
            return name;
        }

    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static class Record {
        final List<Field> fieldDefinitions;
        final List<String> values;

        public Record(List<Field> fieldDefinitions, List<String> values) {
            this.fieldDefinitions = fieldDefinitions;
            this.values = values;
        }

        public List<Field> getFieldDefinitions() {
            return fieldDefinitions;
        }

        public List<String> getValues() {
            return values;
        }
    }
}

