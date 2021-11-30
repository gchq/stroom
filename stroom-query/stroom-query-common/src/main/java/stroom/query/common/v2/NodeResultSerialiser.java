package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;
import java.util.function.Consumer;

public class NodeResultSerialiser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResultSerialiser.class);

    public static boolean read(final Input input,
                               final Coprocessors coprocessors,
                               final Consumer<String> errorConsumer) {

        // Read completion status.
        final boolean complete = input.readBoolean();

        // Read payloads for each coprocessor.
        final boolean allRejected = coprocessors.readPayloads(input);

        // Read all errors.
        final int length = input.readInt();
        for (int i = 0; i < length; i++) {
            final String error = input.readString();
            LOGGER.debug(() -> error);
            errorConsumer.accept(error);
        }

        return complete || allRejected;
    }

    public static void write(final Output output,
                             final boolean complete,
                             final Coprocessors coprocessors,
                             final List<String> errorsSnapshot) {
        // Write completion status.
        output.writeBoolean(complete);

        // Produce payloads for each coprocessor.
        coprocessors.writePayloads(output);

        // Drain all current errors to a list.
        output.writeInt(errorsSnapshot.size());
        for (final String error : errorsSnapshot) {
            LOGGER.debug(() -> error);
            output.writeString(error);
        }
    }

    public static void writeEmptyResponse(final Output output,
                                          final boolean complete) {
        output.writeBoolean(complete);
        // There will be 0 payloads.
        output.writeInt(0);
        // There will be 0 errors.
        output.writeInt(0);
    }
}
