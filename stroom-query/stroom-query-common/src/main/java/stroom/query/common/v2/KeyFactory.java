package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.util.logging.Metrics;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyFactory {

    private final KeyFactoryConfig keyFactoryConfig;
    private final Serialisers serialisers;

    public KeyFactory(final KeyFactoryConfig keyFactoryConfig,
                      final Serialisers serialisers) {
        this.keyFactoryConfig = keyFactoryConfig;
        this.serialisers = serialisers;
    }

    public byte[] getBytes(final Key key, final ErrorConsumer errorConsumer) {
        return Metrics.measure("KeyFactory getBytes", () -> {
            try (final Output output = serialisers.getOutputFactory().createKeyOutput(errorConsumer)) {
                if (keyFactoryConfig.addTimeToKey()) {
                    // Write time.
                    output.writeLong(key.getTimeMs());
                }

                // Write number of key parts.
                output.writeInt(key.getKeyParts().size());

                // Write key parts.
                for (final KeyPart keyPart : key.getKeyParts()) {
                    output.writeBoolean(keyPart.isGrouped());
                    keyPart.write(output);
                }
                output.flush();
                return output.toBytes();
            }
        });
    }

    public Key create(byte[] bytes) {
        return Metrics.measure("KeyFactory create", () -> {
            try (final Input input = serialisers.getInputFactory().create(bytes)) {
                long timeMs = 0;
                if (keyFactoryConfig.addTimeToKey()) {
                    timeMs = input.readLong();
                }
                final int size = input.readInt();
                final List<KeyPart> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    final boolean grouped = input.readBoolean();
                    if (grouped) {
                        list.add(new GroupKeyPart(ValSerialiser.readArray(input)));
                    } else {
                        list.add(new UngroupedKeyPart(input.readLong()));
                    }
                }
                return new Key(timeMs, list);
            }
        });
    }

    public Set<Key> convertSet(final Set<String> openGroups) {
        return Metrics.measure("Converting open groups", () -> {
            Set<Key> keys = Collections.emptySet();
            if (openGroups != null) {
                keys = new HashSet<>();
                for (final String encodedGroup : openGroups) {
                    final byte[] bytes = Base64.getDecoder().decode(encodedGroup);
                    keys.add(create(bytes));
                }
            }
            return keys;
        });
    }

    public String encode(final Key key, final ErrorConsumer errorConsumer) {
        return Metrics.measure("Encoding groups", () ->
                Base64.getEncoder().encodeToString(getBytes(key, errorConsumer)));
    }
}
