package stroom.test.common.data;

import stroom.util.concurrent.AtomicLoopedIntegerSequence;
import stroom.util.concurrent.AtomicLoopedLongSequence;

import com.google.common.base.Preconditions;
import com.google.common.reflect.ClassPath;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// NOTE: This class and the other classes in this package were taken from stroom-test-data at commit
// 4179064. Nothing else was using stroom-test-data, so it seemed easier to bring it back in house.
/**
 * Class for generating test data by constructing field definitions. Each {@link Field} definition
 * defines how the next value will be generated. A number of different types of predefined {@link Field}
 * types are available. For examples of how to use this class see test class TestDataGenerator.
 */
public class DataGenerator {

    // These are all statics owing to the fact that the field definitions are all created by static
    // methods.  Need to refactor them to be methods on some sort of builder that can have these things
    // made available to them.
    // Use a consistent random generator for all fields, so the generated data should be repeatable
    // if a seed is provided.
    private static volatile Random RANDOM = null;
    private static volatile Faker FAKER = null;
    private static volatile Locale LOCALE = null;

    private static final Logger LOGGER = LoggerFactory.getLogger(DataGenerator.class);

    /**
     * Method to begin the process of building a test data generator definition and producing the test data.
     *
     * @return A definition builder
     */
    public static DefinitionBuilder buildDefinition() {
        return new DefinitionBuilder();
    }

    /**
     * @return A pre-canned stream consumer that writes each string to System.out
     */
    public static Consumer<Stream<String>> getSystemOutConsumer() {
        return stringStream ->
                stringStream.forEach(System.out::println);
    }

    /**
     * See {@link #getFileOutputConsumer(Path, String)}
     *
     * @param filePath The path of the file to write, any parent directories will be created.
     * @return A pre-canned stream consumer that writes each string to the file
     * at filePath
     */
    public static Consumer<Stream<String>> getFileOutputConsumer(final Path filePath) {
        Objects.requireNonNull(filePath);

        return getFileOutputConsumer(filePath, "\n");
    }

    /**
     * @param filePath        The path of the file to write, any parent directories will be created.
     * @param recordSeparator The string to separate records with
     * @return A pre-canned stream consumer that writes each string to the file
     * at filePath
     */
    public static Consumer<Stream<String>> getFileOutputConsumer(final Path filePath,
                                                                 final String recordSeparator) {
        Objects.requireNonNull(filePath);
        Objects.requireNonNull(recordSeparator);

        ensureDirectories(filePath);

        return recordStream -> {
            try {
                try (final BufferedWriter writer = new BufferedWriter(
                        new FileWriter(filePath.toFile(), true))) {

                    AtomicBoolean isFirstRecord = new AtomicBoolean(true);
                    recordStream.forEach(recordStr -> {
                        try {
                            if (!isFirstRecord.get() && !recordSeparator.isEmpty()) {
                                writer.append(recordSeparator);
                            }
                            writer.append(recordStr);
                        } catch (IOException e) {
                            throw new RuntimeException("Error writing line to file "
                                    + filePath.toAbsolutePath().normalize().toString() + ": "
                                    + e.getMessage());
                        }
                        isFirstRecord.set(false);
                    });
                }
            } catch (IOException e) {
                throw new RuntimeException("Error writing to file "
                        + filePath.toAbsolutePath().normalize().toString() + ": "
                        + e.getMessage());
            }
        };
    }

    /**
     * Uses java-faker to produce values of various types.
     * See https://github.com/DiUS/java-faker
     * e.g.
     * <code>
     * fakerField("beer", faker -&gt; faker.beer().name(), Locale.UK)
     * </code>
     *
     * @param name          The name of the field
     * @param fakerFunction The function to call on Faker
     * @return A new Field instance
     */
    public static Field fakerField(final String name,
                                   final Function<Faker, String> fakerFunction) {
        try {
            Objects.requireNonNull(name);
            Objects.requireNonNull(fakerFunction);

            final Supplier<String> supplier = () ->
                    fakerFunction.apply(FAKER);
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(
                    Utils.message("Error building fakerField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * Stateful value supplier that supplies a value from values in sequential order
     * looping back to the beginning when it gets to the end.
     *
     * @param name   Field name for use in the header
     * @param values The values to loop through when delivering values to rows
     * @return A complete {@link Field}
     */
    public static Field sequentialValueField(final String name, final List<String> values) {
        try {
            Objects.requireNonNull(values);
            Utils.checkArgument(!values.isEmpty(), "values is empty");
            final AtomicLoopedIntegerSequence indexSequence = new AtomicLoopedIntegerSequence(0, values.size());

            final Supplier<String> supplier = () ->
                    values.get(indexSequence.getNext());
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message(
                    "Error building sequentialValueField, {},{}",
                    name, e.getMessage()), e);
        }
    }

    /**
     * {@link Field} that supplies a random value from values on each call to getNext()
     *
     * @param name   Field name for use in the header
     * @param values The values to randomly select from when delivering values
     * @return A complete {@link Field}
     */
    public static Field randomValueField(final String name, final List<String> values) {
        try {
            Objects.requireNonNull(values);
            Utils.checkArgument(!values.isEmpty(), "values is empty");
            final Supplier<String> supplier = () ->
                    values.get(RANDOM.nextInt(values.size()));
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(
                    Utils.message("Error building randomValueField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * {@link Field} that supplies a random emoticon emoji on each call to getNext()
     *
     * @param name Field name for use in the header
     * @return A complete {@link Field}
     */
    public static Field randomEmoticonEmojiField(final String name) {
        return randomEmojiField(name, 0x1f600, 0x1f64f);
    }

    /**
     * {@link Field} that supplies a random food emoji on each call to getNext()
     *
     * @param name Field name for use in the header
     * @return A complete {@link Field}
     */
    public static Field randomFoodEmojiField(final String name) {
        return randomEmojiField(name, 0x1f32d, 0x1f37f);
    }

    /**
     * {@link Field} that supplies a random animal emoji on each call to getNext()
     *
     * @param name Field name for use in the header
     * @return A complete {@link Field}
     */
    public static Field randomAnimalEmojiField(final String name) {
        return randomEmojiField(name, 0x1f400, 0x1f4d3);
    }

    /**
     * {@link Field} that supplies a random emoji from the supplied list on each call to getNext()
     *
     * @param name       Field name for use in the header
     * @param codePoints A list of code points for the emojis to include.
     * @return A complete {@link Field}
     */
    public static Field randomEmojiField(final String name, final List<Integer> codePoints) {
        Objects.requireNonNull(codePoints);
        Utils.checkArgument(!codePoints.isEmpty(), "codePoints is empty");
        try {
            final Supplier<String> supplier = () -> {
                final int codePoint = codePoints.get(RANDOM.nextInt(codePoints.size()));
                return new StringBuilder().appendCodePoint(codePoint).toString();
            };
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(
                    Utils.message("Error building randomEmojiField, {}, {}", name, e.getMessage()), e);
        }
    }

    private static Field randomEmojiField(final String name,
                                          final int minCodePoint,
                                          final int maxCodePoint) {
        final int range = maxCodePoint - minCodePoint;
        try {
            final Supplier<String> supplier = () -> {
                final int codePoint = RANDOM.nextInt(range) + minCodePoint;
                return new StringBuilder().appendCodePoint(codePoint).toString();
            };
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(
                    Utils.message("Error building randomEmojiField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * @param name         Field name for use in the header
     * @param format       {@link String#format} compatible format containing a single
     *                     placeholder, e.g. "user-%s" or "user-%03d"
     * @param maxNumberExc A random number between 0 (inclusive) and maxNumberExc (exclusive) will
     *                     replace the %s in the format string
     * @return A complete {@link Field}
     */
    public static Field randomNumberedValueField(final String name,
                                                 final String format,
                                                 final int maxNumberExc) {
        try {
            Objects.requireNonNull(format);
            Utils.checkArgument(maxNumberExc > 0, "maxNumberExc must be > 0");

            final Supplier<String> supplier = () ->
                    String.format(format, RANDOM.nextInt(maxNumberExc));
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message(
                    "Error building randomNumberedValueField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * Returns numbered values where the value is defined by a format and the number increases
     * sequentially and loops back round when it hits endEx.
     *
     * @param name     Field name for use in the header
     * @param format   {@link String#format} compatible format containing a single
     *                 placeholder, e.g. "user-%s" or "user-%03d"
     * @param startInc The lowest value to use in the string format (inclusive)
     * @param endExc   The highest value to use in the string format (exclusive)
     *                 replace the %s in the format string
     * @return A complete {@link Field}
     */
    public static Field sequentiallyNumberedValueField(final String name,
                                                       final String format,
                                                       final int startInc,
                                                       final int endExc) {
        try {
            Objects.requireNonNull(format);

            final AtomicLoopedLongSequence numberSequence = new AtomicLoopedLongSequence(
                    startInc,
                    endExc);

            final Supplier<String> supplier = () ->
                    String.format(format, numberSequence.getNext());

            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message(
                    "Error building sequentiallyNumberedValueField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * A field that produces sequential integers starting at startInc (inclusive).
     * If endExc (exclusive) is reached it will loop back round to startInc.
     *
     * @param name     Field name for use in the header
     * @param startInc The lowest value to use in the string format (inclusive)
     * @param endExc   The highest value to use in the string format (exclusive)
     *                 replace the %s in the format string
     * @return A complete {@link Field}
     */
    public static Field sequentialNumberField(final String name,
                                              final long startInc,
                                              final long endExc) {

        try {
            final AtomicLoopedLongSequence numberSequence = new AtomicLoopedLongSequence(
                    startInc,
                    endExc);

            final Supplier<String> supplier = () ->
                    Long.toString(numberSequence.getNext());

            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message(
                    "Error building sequentialValueField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * A field that produces integers between startInc (inclusive) and endExc (exclusive)
     *
     * @param name     Field name for use in the header
     * @param startInc The lowest value to use in the string format (inclusive)
     * @param endExc   The highest value to use in the string format (exclusive)
     *                 replace the %s in the format string
     * @return A complete {@link Field}
     */
    public static Field randomNumberField(final String name,
                                          final int startInc,
                                          final int endExc) {

        try {
            Utils.checkArgument(endExc > startInc, "endExc must be > startInc");

            return new Field(
                    name,
                    () -> Integer.toString(buildRandomNumberSupplier(startInc, endExc).getAsInt()));
        } catch (Exception e) {
            throw new RuntimeException(Utils.message(
                    "Error building sequentialValueField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * A field that produces IP address conforming to [0-9]{1-3}\.[0-9]{1-3}\.[0-9]{1-3}\.[0-9]{1-3}
     *
     * @param name Field name for use in the header
     * @return A complete {@link Field}
     */
    public static Field randomIpV4Field(final String name) {

        try {
            final IntSupplier intSupplier = buildRandomNumberSupplier(0, 256);

            final Supplier<String> supplier = () ->
                    String.format("%d.%d.%d.%d",
                            intSupplier.getAsInt(),
                            intSupplier.getAsInt(),
                            intSupplier.getAsInt(),
                            intSupplier.getAsInt());
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message(
                    "Error building randomIpV4Field, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * A field to produce a sequence of random datetime values within a defined time range.
     * The formatter controls the output format.
     *
     * @param name         Field name for use in the header
     * @param startDateInc The start date for the random times (inclusive)
     * @param endDateExc   The end date of random times (exclusive)
     * @param formatStr    Format string conforming to the format expected by {@link DateTimeFormatter}
     * @return A complete {@link Field}
     */
    public static Field randomDateTimeField(final String name,
                                            final LocalDateTime startDateInc,
                                            final LocalDateTime endDateExc,
                                            final String formatStr) {
        try {
            Objects.requireNonNull(startDateInc);
            Objects.requireNonNull(endDateExc);
            Objects.requireNonNull(formatStr);
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatStr);
            return randomDateTimeField(name, startDateInc, endDateExc, dateTimeFormatter);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message("Error building randomDateTimeField, {}, {}",
                    name,
                    e.getMessage()), e);
        }
    }

    /**
     * A field to produce a sequence of random datetime values within a defined time range.
     * The formatter controls the output format.
     *
     * @param name         Field name for use in the header
     * @param startDateInc The start date for the random times (inclusive)
     * @param endDateExc   The end date of random times (exclusive)
     * @param formatter    {@link DateTimeFormatter} to use
     * @return A complete {@link Field}
     */
    public static Field randomDateTimeField(final String name,
                                            final LocalDateTime startDateInc,
                                            final LocalDateTime endDateExc,
                                            final DateTimeFormatter formatter) {
        try {
            Objects.requireNonNull(startDateInc);
            Objects.requireNonNull(endDateExc);
            Objects.requireNonNull(formatter);
            Utils.checkArgument(
                    endDateExc.isAfter(startDateInc),
                    "endDateExc [{}] must be after startDateInc [{}]",
                    endDateExc,
                    startDateInc);

            final long millisBetween = endDateExc.toInstant(ZoneOffset.UTC).toEpochMilli()
                    - startDateInc.toInstant(ZoneOffset.UTC).toEpochMilli();

            final Supplier<String> supplier = () -> {
                try {
                    final long randomDelta = (long) (RANDOM.nextDouble() * millisBetween);
                    final LocalDateTime dateTime = startDateInc.plus(randomDelta, ChronoUnit.MILLIS);
                    return dateTime.format(formatter);
                } catch (Exception e) {
                    throw new RuntimeException(Utils.message("Time range is too large, maximum allowed: {}",
                            Duration.ofMillis(Integer.MAX_VALUE).toString()), e);
                }
            };
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message("Error building randomDateTimeField, {}, {}",
                    name,
                    e.getMessage()), e);
        }
    }

    /**
     * A field to produce a sequence of datetime values with a constant delta based on
     * a configured start datetime and delta. The formatter controls the output format.
     *
     * @param name         Field name for use in the header
     * @param startDateInc The start date for the random times (inclusive)
     * @param delta        The delta to apply to successive values
     * @param formatStr    Format string conforming to the format expected by {@link DateTimeFormatter}
     * @return A complete {@link Field}
     */
    public static Field sequentialDateTimeField(final String name,
                                                final LocalDateTime startDateInc,
                                                final Duration delta,
                                                final String formatStr) {
        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(formatStr);
            return sequentialDateTimeField(name, startDateInc, delta, dateTimeFormatter);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(Utils.message("Error building sequentialDateTimeField, {}, {}",
                    name,
                    e.getMessage()), e);
        }
    }

    /**
     * A field to produce a sequence of datetime values with a constant delta based on
     * a configured start datetime and delta. The formatter controls the output format.
     *
     * @param name         Field name for use in the header
     * @param startDateInc The start date for the random times (inclusive)
     * @param delta        The delta to apply to successive values
     * @param formatter    {@link DateTimeFormatter} to use
     * @return A complete {@link Field}
     */
    public static Field sequentialDateTimeField(final String name,
                                                final LocalDateTime startDateInc,
                                                final Duration delta,
                                                final DateTimeFormatter formatter) {
        try {
            final AtomicReference<LocalDateTime> lastValueRef = new AtomicReference<>(startDateInc);

            final Supplier<String> supplier = () -> {
                try {
                    return lastValueRef.getAndUpdate(lastVal -> lastVal.plus(delta))
                            .format(formatter);
                } catch (Exception e) {
                    throw new RuntimeException(Utils.message("Time range is too large, maximum allowed: {}",
                            Duration.ofMillis(Integer.MAX_VALUE).toString()));
                }
            };
            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message("Error building sequentialDateTimeField, {}, {}",
                    name,
                    e.getMessage()), e);
        }
    }

    /**
     * A field that produces a new random UUID on each call to getNext().
     * Will always be random even if a seed is used for the generator.
     *
     * @param name Field name for use in the header
     * @return A complete {@link Field}
     */
    public static Field uuidField(final String name) {
        try {
            return new Field(name, () -> UUID.randomUUID().toString());
        } catch (Exception e) {
            throw new RuntimeException(Utils.message("Error building uuidField, {}, {}", name, e.getMessage()), e);
        }
    }

    /**
     * A field populated with a random number (between minCount and maxCount) of
     * words separated by ' ' as picked randomly from wordList
     *
     * @param name     Field name for use in the header
     * @param minCount The minimum number of words to use when generation values
     * @param maxCount The maximum number of words to use when generation values
     * @param wordList The list of words to choose from when building values
     * @return A complete {@link Field}
     */
    public static Field randomWordsField(final String name,
                                         final int minCount,
                                         final int maxCount,
                                         final List<String> wordList) {
        try {
            Utils.checkArgument(minCount >= 0, "minCount must be >= 0");
            Utils.checkArgument(maxCount >= minCount, "maxCount must be >= minCount");
            Objects.requireNonNull(wordList);
            Utils.checkArgument(wordList.size() > 0,
                    () -> Utils.message(
                            "wordList must have size greater than zero, size {}",
                            wordList.size()));

            Supplier<String> supplier = () -> {
                int wordCount = RANDOM.nextInt(maxCount - minCount + 1) + minCount;
                return IntStream.rangeClosed(0, wordCount)
                        .boxed()
                        .map(i -> wordList.get(RANDOM.nextInt(wordList.size())))
                        .collect(Collectors.joining(" "))
                        .replaceAll("(^\\s+|\\s+$)", "") //remove leading/trailing spaces
                        .replaceAll("\\s\\s+", " "); //replace multiple spaces with one
            };

            return new Field(name, supplier);
        } catch (Exception e) {
            throw new RuntimeException(Utils.message("Error building randomWordsField, {}, {}", name, e.getMessage()),
                    e);
        }
    }

    /**
     * A field populated with a random number (between minCount and maxCount) of
     * words separated by ' '. The words are picked at random from all the class
     * names in the 'java' package on the classpath
     */
    public static Field randomClassNamesField(final String name,
                                              final int minCount,
                                              final int maxCount) {
        try {
            final List<String> classNames;
            try {
                classNames = ClassNamesListHolder.getClassNames();
            } catch (final RuntimeException e) {
                throw new RuntimeException(String.format("Error getting class names list", e.getMessage()), e);
            }

            Preconditions.checkNotNull(classNames);
            Preconditions.checkArgument(!classNames.isEmpty(),
                    "classNames cannot be empty, something has gone wrong finding the class names");

            return randomWordsField(name, minCount, maxCount, classNames);
        } catch (final RuntimeException e) {
            throw new RuntimeException(String.format("Error building randomClassNamesField, %s, %s",
                    name,
                    e.getMessage()), e);
        }
    }

    private static IntSupplier buildRandomNumberSupplier(final int startInc,
                                                         final int endExc) {
        try {
            Utils.checkArgument(endExc > startInc, "endExc must be >  startInc");

            final int delta = endExc - startInc;

            return () ->
                    RANDOM.nextInt(delta) + startInc;
        } catch (Exception e) {
            throw new RuntimeException(Utils.message("Error building randomNumberSupplier, {}", e.getMessage()), e);
        }
    }

    private static void ensureDirectories(final Path file) {
        if (file.getParent() != null) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Error creating parent directories for {}"
                        + file.toAbsolutePath().normalize().toString());
            }
        }
    }


    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    /**
     * Holder class for the static class names list to allow for lazy initialisation
     */
    private static class ClassNamesListHolder {

        private static final List<String> classNames;

        public static final List<String> NOT_FOUND_LIST = Collections.singletonList("ERROR_NO_CLASS_NAMES_FOUND");

        static {
            //lazy initialisation
            classNames = generateList();
//            System.out.println("ClassNames size: " + classNames.size());
        }

        public static List<String> getClassNames() {
            return classNames;
        }

        private static List<String> generateList() {

            try {
                final ClassLoader loader = Thread.currentThread().getContextClassLoader();

                List<String> classNames = ClassPath.from(loader).getAllClasses().stream()
                        .filter(classInfo -> classInfo.getPackageName().startsWith("java."))
                        .map(ClassPath.ClassInfo::getSimpleName)
                        .collect(Collectors.toList());

                if (classNames.isEmpty()) {
                    return NOT_FOUND_LIST;
                } else {
                    return classNames;
                }
            } catch (final IOException e) {
                LOGGER.error("Error reading classloader", e);
                return NOT_FOUND_LIST;
            }
        }
    }


    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class DefinitionBuilder {

        private List<Field> fieldDefinitions = new ArrayList<>();
        private Consumer<Stream<String>> rowStreamConsumer;
        private int rowCount = 1;
        private DataWriter dataWriter;
        private boolean isParallel = false;
        private Random random = null;
        private Locale locale = null;

        public DefinitionBuilder addFieldDefinition(final Field fieldDefinition) {
            boolean isNamedAlreadyUsed = fieldDefinitions.stream()
                    .map(Field::getName)
                    .anyMatch(Predicate.isEqual(fieldDefinition.getName()));
            Utils.checkArgument(!isNamedAlreadyUsed,
                    () -> Utils.message("Name [{}] is already in use", fieldDefinition.getName()));

            fieldDefinitions.add(Objects.requireNonNull(fieldDefinition));
            return this;
        }

        public DefinitionBuilder consumedBy(final Consumer<Stream<String>> rowStreamConsumer) {
            this.rowStreamConsumer = Objects.requireNonNull(rowStreamConsumer);
            return this;
        }

        public DefinitionBuilder setDataWriter(final DataWriter dataWriter) {
            this.dataWriter = Objects.requireNonNull(dataWriter);
            return this;
        }

        public DefinitionBuilder rowCount(final int rowCount) {
            Utils.checkArgument(rowCount > 0, "rowCount must be > 0");
            this.rowCount = rowCount;
            return this;
        }

        public DefinitionBuilder multiThreaded() {
            if (random != null) {
                this.isParallel = true;
            }
            return this;
        }

        public DefinitionBuilder withRandomSeed(long seed) {
            this.random = new Random(seed);
            // Can't run in parallel if using a fixed seed
            this.isParallel = false;
            return this;
        }

        public DefinitionBuilder withLocale(final Locale locale) {
            this.locale = Objects.requireNonNull(locale);
            return this;
        }

        public void generate() {
            DataGenerator.RANDOM = random != null
                    ? random
                    : new Random();
            DataGenerator.LOCALE = locale != null
                    ? locale
                    : Locale.getDefault();
            DataGenerator.FAKER = new Faker(DataGenerator.LOCALE, DataGenerator.RANDOM);

            if (fieldDefinitions.isEmpty()) {
                throw new RuntimeException("No field definitions defined");
            }
            if (rowStreamConsumer == null) {
                throw new RuntimeException("No consumer defined");
            }
            if (dataWriter == null) {
                //default to CSV
                dataWriter = FlatDataWriterBuilder.defaultCsvFormat();
            }

            //convert our stream of data records into a stream of strings that possibly
            //includes adding things like header/footer rows, tags, delimiters, etc.
            final Stream<String> rowStream = dataWriter.mapRecords(fieldDefinitions, generateDataRows());

            // The consumers, e.g. file writers are likely not thread safe so make the stream sequential at this
            // point
            rowStreamConsumer.accept(rowStream.sequential());
        }

        private Stream<DataRecord> generateDataRows() {

            Function<Integer, DataRecord> toRecordMapper = integer -> {
                List<String> values = fieldDefinitions.stream()
                        .map(field -> {
                            try {
                                return field.getNext();
                            } catch (Exception e) {
                                throw new RuntimeException(Utils.message("Error getting next value for field {}, {}",
                                        field.getName(), e.getMessage()), e);
                            }
                        })
                        .collect(Collectors.toList());
                return new DataRecord(fieldDefinitions, values);
            };

            IntStream stream = IntStream.rangeClosed(1, rowCount);

            if (isParallel) {
                stream = stream.parallel();
            } else {
                stream = stream.sequential();
            }

            return stream
                    .boxed()
                    .map(toRecordMapper);
        }
    }
}

