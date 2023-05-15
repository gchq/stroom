package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.MyByteBufferInput;
import stroom.util.logging.Metrics;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class KeyFactoryFactory {

    private KeyFactoryFactory() {
        // Non instantiable.
    }

    public static KeyFactory create(final KeyFactoryConfig keyFactoryConfig,
                                    final CompiledDepths compiledDepths) {
        final boolean flat = compiledDepths.getMaxDepth() == 0 &&
                compiledDepths.getMaxGroupDepth() <= compiledDepths.getMaxDepth();
        if (flat) {
            if (keyFactoryConfig.addTimeToKey()) {
                if (compiledDepths.hasGroup()) {
                    return new FlatTimeGroupedKeyFactory();
                } else {
                    return new FlatTimeUngroupedKeyFactory();
                }
            } else {
                if (compiledDepths.hasGroup()) {
                    return new FlatGroupedKeyFactory();
                } else {
                    return new FlatUngroupedKeyFactory();
                }
            }
        } else {
            if (keyFactoryConfig.addTimeToKey()) {
                return new NestedTimeGroupedKeyFactory();

            } else {
                return new NestedGroupedKeyFactory();
            }
        }
    }

    private abstract static class AbstractKeyFactory implements KeyFactory {

        private final AtomicLong ungroupedItemSequenceNumber = new AtomicLong();
        private int bufferSize = 8;

        @Override
        public Key read(final ByteBuffer byteBuffer) {
            try (final MyByteBufferInput input = new MyByteBufferInput(byteBuffer)) {
                return read(input);
            }
        }

        @Override
        public Set<Key> decodeSet(final Set<String> openGroups) {
            return Metrics.measure("Converting open groups", () -> {
                Set<Key> keys = Collections.emptySet();
                if (openGroups != null) {
                    keys = new HashSet<>();
                    for (final String encodedGroup : openGroups) {
                        final ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encodedGroup));
                        keys.add(read(buffer));
                    }
                }
                return keys;
            });
        }

        @Override
        public String encode(final Key key, final ErrorConsumer errorConsumer) {
            return Metrics.measure("Encoding groups", () -> {
                try (final Output output = new Output(bufferSize, -1)) {
                    write(key, output);
                    output.flush();
                    final byte[] bytes = output.toBytes();
                    bufferSize = Math.max(bufferSize, output.getBuffer().length);
                    return Base64.getEncoder().encodeToString(bytes);
                }
            });
        }

        @Override
        public long getUniqueId() {
            return ungroupedItemSequenceNumber.incrementAndGet();
        }
    }

    /**
     * Creates a flat group key. <GROUP VAL ARRAY>
     */
    private static class FlatGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final Output output) {
            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().get(0);
            keyPart.write(output);
        }

        @Override
        public Key read(final Input input) {
            // Read single key part.
            final GroupKeyPart groupKeyPart = new GroupKeyPart(ValSerialiser.readArray(input));
            final List<KeyPart> list = Collections.singletonList(groupKeyPart);
            return new Key(0, list);
        }
    }

    /**
     * Creates a flat unique key. <UNIQUE_ID>
     */
    private static class FlatUngroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final Output output) {
            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().get(0);
            keyPart.write(output);
        }

        @Override
        public Key read(final Input input) {
            // Read single key part.
            final UngroupedKeyPart keyPart = new UngroupedKeyPart(input.readLong());
            final List<KeyPart> list = Collections.singletonList(keyPart);
            return new Key(0, list);
        }
    }

    /**
     * Creates a flat time based group key. <TIME_MS><GROUP VAL ARRAY>
     */
    private static class FlatTimeGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final Output output) {
            // Write time millis since epoch.
            output.writeLong(key.getTimeMs());

            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().get(0);
            keyPart.write(output);
        }

        @Override
        public Key read(final Input input) {
            // Read time millis since epoch.
            final long timeMs = input.readLong();

            // Read single key part.
            final GroupKeyPart groupKeyPart = new GroupKeyPart(ValSerialiser.readArray(input));
            final List<KeyPart> list = Collections.singletonList(groupKeyPart);
            return new Key(timeMs, list);
        }
    }

    /**
     * Creates flat time based unique key. <TIME_MS><UNIQUE_ID>
     */
    private static class FlatTimeUngroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final Output output) {
            // Write time millis since epoch.
            output.writeLong(key.getTimeMs());

            // Write single key part.
            final KeyPart keyPart = key.getKeyParts().get(0);
            keyPart.write(output);
        }

        @Override
        public Key read(final Input input) {
            // Read time millis since epoch.
            final long timeMs = input.readLong();

            // Read single key part.
            final UngroupedKeyPart keyPart = new UngroupedKeyPart(input.readLong());
            final List<KeyPart> list = Collections.singletonList(keyPart);
            return new Key(timeMs, list);
        }
    }

    /**
     * Creates a nested group key. <DEPTH><LIST OF GROUP VAL ARRAYS>
     */
    private static class NestedGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final Output output) {
            // Write number of key parts (depth).
            output.writeByte(key.getKeyParts().size());

            // Write key parts.
            for (final KeyPart keyPart : key.getKeyParts()) {
                // Record if this is a grouped part.
                output.writeBoolean(keyPart.isGrouped());
                keyPart.write(output);
            }
        }

        @Override
        public Key read(final Input input) {
            // Read number of key parts (depth).
            final int size = Byte.toUnsignedInt(input.readByte());

            // Read key parts.
            final List<KeyPart> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                // Determine if this is a grouped part.
                final boolean grouped = input.readBoolean();
                if (grouped) {
                    list.add(new GroupKeyPart(ValSerialiser.readArray(input)));
                } else {
                    list.add(new UngroupedKeyPart(input.readLong()));
                }
            }
            return new Key(0, list);
        }
    }

    /**
     * Creates a nested time based group key. <DEPTH><TIME_MS><LIST OF GROUP VAL ARRAYS>
     */
    private static class NestedTimeGroupedKeyFactory extends AbstractKeyFactory implements KeyFactory {

        @Override
        public void write(final Key key, final Output output) {
            // Write number of key parts (depth).
            output.writeByte(key.getKeyParts().size());

            // Write time millis since epoch.
            output.writeLong(key.getTimeMs());

            // Write key parts.
            for (final KeyPart keyPart : key.getKeyParts()) {
                // Record if this is a grouped part.
                output.writeBoolean(keyPart.isGrouped());
                keyPart.write(output);
            }
        }

        @Override
        public Key read(final Input input) {
            // Read number of key parts (depth).
            final int size = Byte.toUnsignedInt(input.readByte());

            // Read time millis since epoch.
            final long timeMs = input.readLong();

            // Read key parts.
            final List<KeyPart> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                // Determine if this is a grouped part.
                final boolean grouped = input.readBoolean();
                if (grouped) {
                    list.add(new GroupKeyPart(ValSerialiser.readArray(input)));
                } else {
                    list.add(new UngroupedKeyPart(input.readLong()));
                }
            }
            return new Key(timeMs, list);
        }
    }
}
