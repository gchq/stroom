package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;
import java.util.function.Consumer;

public class NodeResultSerialiser {

    public static boolean read(final Input input,
                               final Coprocessors coprocessors,
                               final Consumer<String> errorConsumer,
                               final Consumer<Boolean> completionConsumer) {
        boolean success;

        // Read completion status.
        completionConsumer.accept(input.readBoolean());

        // Read payloads for each coprocessor.
        success = coprocessors.readPayloads(input);

        // Read all errors.
        final int length = input.readInt();
        for (int i = 0; i < length; i++) {
            errorConsumer.accept(input.readString());
        }

        return success;
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
