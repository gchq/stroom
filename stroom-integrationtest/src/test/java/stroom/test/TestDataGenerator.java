package stroom.test;

import com.google.common.base.Preconditions;
import com.google.common.reflect.ClassPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
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


    //    private TestDataGenerator(final List<Field> fieldDefinitions,
//                              final Consumer<Stream<String>> rowStreamConsumer,
//                              final int rowCount,
//                              final boolean isHeaderIncluded) {
//
//
//    }
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
     * words separated by ' '.
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

    public static class Builder {
        private List<Field> fieldDefinitions = new ArrayList<>();
        private Consumer<Stream<String>> rowStreamConsumer;
        private int rowCount = 1;
        private boolean isHeaderIncluded = true;
        private String delimiter = ",";
        private Optional<String> optEnclosingChars = Optional.empty();

//        private Builder(final List<Field> fieldDefinitions,
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

        public Builder addFieldDefinition(final Field fieldDefinition) {
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
                    .map(Field::getName)
                    .map(enclosureMapper)
                    .collect(Collectors.joining(delimiter));
            return Stream.of(header);
        }

        private Stream<String> generateDataRows() {

            final Function<String, String> enclosureMapper = getEnclosureMapper();
            Function<Integer, String> mapper = integer ->
                    fieldDefinitions.stream()
                            .map(Field::getNext)
                            .map(enclosureMapper)
                            .collect(Collectors.joining(delimiter));

            return IntStream.rangeClosed(0, rowCount)
                    .sequential()
                    .boxed()
                    .map(mapper);
        }

    }

    /**
     * Class to hold the definition of a field in a set of flat test data records.
     * Multiple static factory methods exist for creating various pre-canned types
     * of test data field, e.g. a random IP address
     */
    public static class Field {

        private static final Logger LOGGER = LoggerFactory.getLogger(Field.class);

        private final String name;
        private final Supplier<String> valueFunction;

        public Field(final String name,
                     final Supplier<String> valueSupplier) {

            this.name = Preconditions.checkNotNull(name);
            this.valueFunction = Preconditions.checkNotNull(valueSupplier);
        }

        public String getNext() {
            return valueFunction.get();
        }

        public String getName() {
            return name;
        }


    }
}
