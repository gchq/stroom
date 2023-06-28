package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;

public class NodeResultSerialiser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResultSerialiser.class);

    public static boolean read(final Input input,
                               final Coprocessors coprocessors,
                               final ErrorConsumer errorConsumer) {

        // Read completion status.
        final boolean complete = input.readBoolean();

        // Read payloads for each coprocessor.
        coprocessors.readPayloads(input);

        // Read all errors.
        final int length = input.readInt();
        for (int i = 0; i < length; i++) {
            final String error = input.readString();
            errorConsumer.add(() -> error);
        }

        return complete;
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
        if (errorsSnapshot != null) {
            output.writeInt(errorsSnapshot.size());
            for (final String error : errorsSnapshot) {
                LOGGER.debug(() -> error);
                output.writeString(error);
            }
        } else {
            output.writeInt(0);
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
