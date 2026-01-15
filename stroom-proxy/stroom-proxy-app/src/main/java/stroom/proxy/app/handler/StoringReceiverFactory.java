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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

public class StoringReceiverFactory implements ReceiverFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoringReceiverFactory.class);

    private final SimpleReceiver simpleReceiver;
    private final ZipReceiver zipReceiver;

    public StoringReceiverFactory(final SimpleReceiver simpleReceiver,
                                  final ZipReceiver zipReceiver) {
        this.simpleReceiver = simpleReceiver;
        this.zipReceiver = zipReceiver;
    }

    @Override
    public Receiver get(final AttributeMap attributeMap) {
        // Treat differently depending on compression type.
        final String key = StandardHeaderArguments.COMPRESSION;
        String compression = attributeMap.get(key);
        if (NullSafe.isNonEmptyString(compression)) {
            compression = compression.toUpperCase(StreamUtil.DEFAULT_LOCALE);
            if (!StandardHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                throw new StroomStreamException(
                        StroomStatusCode.UNKNOWN_COMPRESSION, attributeMap, compression);
            }
            // Put the normalised value back in the map
            attributeMap.put(key, compression);
        }

        if (StandardHeaderArguments.COMPRESSION_ZIP.equalsIgnoreCase(compression)) {
            // Handle a zip stream.
            LOGGER.debug("get() - Using zipReceiver, compression: {}, attributeMap: {}",
                    compression, attributeMap);
            return zipReceiver;

        } else {
            // Handle non zip streams.
            LOGGER.debug("get() - Using simpleReceiver, compression: {}, attributeMap: {}",
                    compression, attributeMap);
            return simpleReceiver;
        }
    }
}
