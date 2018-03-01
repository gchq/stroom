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
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class to hold the definition of a field in a set of flat test data records.
 * Multiple static factory methods exist for creating various pre-canned types
 * of test data field, e.g. a random IP address
 */
public class TestDataFieldDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataFieldDefinition.class);

    private final String name;
    private final Supplier<String> valueFunction;

    public TestDataFieldDefinition(final String name,
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

    public static TestDataFieldDefinition sequentialValueField(final String name, final List<String> values) {
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
        return new TestDataFieldDefinition(name, supplier);
    }

    public static TestDataFieldDefinition randomValueField(final String name, final List<String> values) {
        Preconditions.checkNotNull(values);
        Preconditions.checkArgument(!values.isEmpty());
        final Random random = new Random();
        final Supplier<String> supplier = () ->
                values.get(random.nextInt(values.size()));
        return new TestDataFieldDefinition(name, supplier);
    }

    /**
     * @param name         Field name for use in the header
     * @param format       A {@link String:format} compatible format containing a single
     *                     placeholder, e.g. "user-%s" or "user-%03d"
     * @param maxNumberExc A random number between 0 (inclusive) and maxNumberExc (exclusive) will
     *                     replace the %s in the format string
     * @return A complete {@link TestDataFieldDefinition}
     */
    public static TestDataFieldDefinition randomNumberedValueField(final String name,
                                                                   final String format,
                                                                   final int maxNumberExc) {
        Preconditions.checkNotNull(format);
        Preconditions.checkArgument(maxNumberExc > 0);

        final Random random = new Random();
        final Supplier<String> supplier = () ->
                String.format(format, random.nextInt(maxNumberExc));
        return new TestDataFieldDefinition(name, supplier);
    }

    public static TestDataFieldDefinition sequentialNumberField(final String name,
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
        return new TestDataFieldDefinition(name, supplier);
    }

    private static IntSupplier buildRandomNumberSupplier(final int startInc,
                                                         final int endExc) {
        Preconditions.checkArgument(endExc > startInc);

        final Random random = new Random();
        final int delta = endExc - startInc;

        return () ->
                random.nextInt(delta) + startInc;
    }

    public static TestDataFieldDefinition randomNumberField(final String name,
                                                            final int startInc,
                                                            final int endExc) {

        Preconditions.checkArgument(endExc > startInc);

        return new TestDataFieldDefinition(
                name,
                () -> Integer.toString(buildRandomNumberSupplier(startInc, endExc).getAsInt()));
    }

    public static TestDataFieldDefinition randomIpV4Field(final String name) {

        final IntSupplier intSupplier = buildRandomNumberSupplier(0, 256);

        final Supplier<String> supplier = () ->
                String.format("%d.%d.%d.%d",
                        intSupplier.getAsInt(),
                        intSupplier.getAsInt(),
                        intSupplier.getAsInt(),
                        intSupplier.getAsInt());
        return new TestDataFieldDefinition(name, supplier);
    }

    public static TestDataFieldDefinition randomDateTime(final String name,
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
        return new TestDataFieldDefinition(name, supplier);
    }

    public static TestDataFieldDefinition sequentialDateTime(final String name,
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
        return new TestDataFieldDefinition(name, supplier);
    }

    public static TestDataFieldDefinition uuid(final String name) {
        return new TestDataFieldDefinition(name, () -> UUID.randomUUID().toString());
    }

    /**
     * A field populated with a random number (between minCount and maxCount) of
     * words separated by ' '. The words are picked at random from all the class
     * names in the 'stroom' package on the classpath
     */
    public static TestDataFieldDefinition randomClassNames(final String name,
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
    public static TestDataFieldDefinition randomWords(final String name,
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

        return new TestDataFieldDefinition(name, supplier);
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

}
