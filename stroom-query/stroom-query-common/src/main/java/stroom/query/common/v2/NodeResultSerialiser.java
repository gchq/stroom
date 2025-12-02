package stroom.query.common.v2;

import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NodeResultSerialiser {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResultSerialiser.class);
    private static final Pattern ERROR_MESSAGE_PATTERN = getErrorMessagePattern();

    public static boolean read(final Input input,
                               final Coprocessors coprocessors,
                               final ErrorConsumer errorConsumer) {

        // Read completion status.
        final boolean complete = input.readBoolean();

        // Read payloads for each coprocessor.
        coprocessors.readPayloads(input);

        consumeErrors(input, errorConsumer);

        return complete;
    }

    private static void consumeErrors(final Input input, final ErrorConsumer errorConsumer) {
        // Read all errors.
        final int length = input.readInt();
        for (int i = 0; i < length; i++) {
            final String error = input.readString();
            if (NullSafe.isNonBlankString(error)) {
                // error is like:
                // 'WARN:Truncating string to 10 characters: fuga bland'
                final Matcher matcher = ERROR_MESSAGE_PATTERN.matcher(error);
                if (matcher.matches()) {
                    final String message = matcher.group(2);
                    try {
                        final Severity severity = Severity.valueOf(matcher.group(1));
                        errorConsumer.add(severity, () -> message);
                    } catch (final Exception e) {
                        // Can't establish severity
                        errorConsumer.add(() -> message);
                    }
                } else {
                    errorConsumer.add(() -> error);
                }
            } else {
                errorConsumer.add(Severity.ERROR, () -> "Blank error");
            }
        }
    }

    public static void write(final Output output,
                             final boolean complete,
                             final Coprocessors coprocessors,
                             final List<ErrorMessage> errorsSnapshot) {
        // Write completion status.
        output.writeBoolean(complete);

        // Produce payloads for each coprocessor.
        coprocessors.writePayloads(output);

        // Drain all current errors to a list.
        if (errorsSnapshot != null) {
            output.writeInt(errorsSnapshot.size());
            for (final ErrorMessage error : errorsSnapshot) {
                LOGGER.debug("write() - error: {}", error);
                final String errorStr;
                if (error.getSeverity() != null) {
                    errorStr = String.join(":",
                            error.getSeverity().name(),
                            NullSafe.string(error.getMessage()));
                } else {
                    errorStr = NullSafe.string(error.getMessage());
                }
                output.writeString(errorStr);
            }
        } else {
            output.writeInt(0);
        }
    }

    private static Pattern getErrorMessagePattern() {
        final List<String> severityValues = Stream.of(Severity.values())
                .map(Severity::name) // Must be consistent with the serialisation used in write()
                .map(Pattern::quote)
                .toList();
        return Pattern.compile("^(" + String.join("|", severityValues) + "):(.*)$");
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
