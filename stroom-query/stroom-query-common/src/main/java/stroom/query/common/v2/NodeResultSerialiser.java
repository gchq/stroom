/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

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
            final String errorJson = input.readString();
            if (NullSafe.isNonBlankString(errorJson)) {
                try {
                    final ErrorMessage errorMessage = JsonUtil.readValue(errorJson, ErrorMessage.class);
                    errorConsumer.add(errorMessage);
                } catch (final Exception e) {
                    LOGGER.debug("Cannot deserialise error message.", e);
                    errorConsumer.add(Severity.ERROR, () -> errorJson);
                }
            }
        }

        return complete;
    }

    public static void write(final Output output,
                             final boolean complete,
                             final Coprocessors coprocessors,
                             final List<ErrorMessage> errors) {
        // Write completion status.
        output.writeBoolean(complete);

        // Produce payloads for each coprocessor.
        coprocessors.writePayloads(output);

        // Drain all current errors to a list.
        if (errors != null) {
            output.writeInt(errors.size());
            for (final ErrorMessage error : errors) {
                LOGGER.debug(error::toString);
                try {
                    output.writeString(JsonUtil.writeValueAsString(error, false));
                } catch (final Exception e) {
                    LOGGER.debug("Cannot serialise error message.", e);
                }
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
