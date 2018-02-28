package stroom.search;

import com.google.common.base.Preconditions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class NetworkMonitoringDataGenerator {

    //Date, Time, EventType, Device, UserName, ID, ErrorCode, IPAddress, Server, Message
    //18/12/2007,13:21:48,authorisationFailed,device4,user5,192.168.0.2,E0567,192.168.0.3,server4,Another message that I made up 2

    public static void main(String[] args) {


    }

    public static class FieldDefinition {
        private final String name;
        private final Supplier<String> valueFunction;

        public FieldDefinition(final String name,
                               final Supplier<String> valueSupplier) {

            this.name = Preconditions.checkNotNull(name);
            this.valueFunction = Preconditions.checkNotNull(valueSupplier);
        }

        public String getNext() {
            return valueFunction.get();
        }

        public static FieldDefinition sequentialValueField(final String name, final List<String> values) {
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
            return new FieldDefinition(name, supplier);
        }

        public static FieldDefinition randomValueField(final String name, final List<String> values) {
            Preconditions.checkNotNull(values);
            Preconditions.checkArgument(!values.isEmpty());
            final Random random = new Random();
            final Supplier<String> supplier = () ->
                    values.get(random.nextInt(values.size()));
            return new FieldDefinition(name, supplier);
        }

        public static FieldDefinition sequentialNumberField(final String name,
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
            return new FieldDefinition(name, supplier);
        }

        private static IntSupplier buildRandomNumberSupplier(final int startInc,
                                                             final int endExc) {
            Preconditions.checkArgument(endExc > startInc);

            final Random random = new Random();
            final int delta = endExc - startInc;

            return () ->
                    random.nextInt(delta) + startInc;
        }

        public static FieldDefinition randomNumberField(final String name,
                                                        final int startInc,
                                                        final int endExc) {

            Preconditions.checkArgument(endExc > startInc);

            return new FieldDefinition(
                    name,
                    () -> Integer.toString(buildRandomNumberSupplier(startInc, endExc).getAsInt()));
        }

        public static FieldDefinition randomIpV4Field(final String name) {


            final Random random = new Random();
            final IntSupplier intSupplier = buildRandomNumberSupplier(0, 256);

            final Supplier<String> supplier = () ->
                    String.format("%03d", intSupplier.getAsInt());
            return new FieldDefinition(name, supplier);
        }


        public static FieldDefinition randomDateTime(final String name,
                                                     final LocalDateTime startDateInc,
                                                     final LocalDateTime endDateExc,
                                                     final DateTimeFormatter formatter) {
            final Random random = new Random();
            final Supplier<String> supplier = () -> {
                try {
                    int millisBetween = (int) Duration.between(startDateInc, endDateExc).toMillis();
                    return startDateInc.plus(random.nextInt(millisBetween), ChronoUnit.MILLIS).format(formatter);
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Time range is too large, maximum allowed: %s",
                            Duration.ofMillis(Integer.MAX_VALUE).toString()));
                }
            };
            return new FieldDefinition(name, supplier);
        }

        public static FieldDefinition sequentialDateTime(final String name,
                                                         final LocalDateTime startDateInc,
                                                         final Duration delta,
                                                         final DateTimeFormatter formatter) {
            final Random random = new Random();
            final AtomicInteger iteration = new AtomicInteger(0);

            final Supplier<String> supplier = () -> {
                try {
                    return startDateInc.plus(delta).format(formatter);
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Time range is too large, maximum allowed: %s",
                            Duration.ofMillis(Integer.MAX_VALUE).toString()));
                }
            };
            return new FieldDefinition(name, supplier);
        }
    }


}
