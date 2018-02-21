/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.shared.Feed;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.StreamUtil;

import java.nio.charset.Charset;

public final class EncodingSelection {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncodingSelection.class);

    private EncodingSelection() {
        // Utility class.
    }

    public static String select(final Feed feed, final StreamType streamType) {
        String encoding = null;
        if (streamType != null) {
            if (StreamType.CONTEXT.equals(streamType)) {
                encoding = feed.getContextEncoding();
            } else if (streamType.isStreamTypeRaw()) {
                encoding = feed.getEncoding();
            }
        }

        if (encoding == null || encoding.trim().length() == 0) {
            encoding = StreamUtil.DEFAULT_CHARSET_NAME;
        }

        // Make sure the requested charset is supported.
        boolean supported = false;
        try {
            supported = Charset.isSupported(encoding);
        } catch (final Exception e) {
            // Ignore.
        }
        if (!supported) {
            LOGGER.error(
                    "Unsupported charset '" + encoding + "'. Using default '" + StreamUtil.DEFAULT_CHARSET_NAME + "'.");
            encoding = StreamUtil.DEFAULT_CHARSET_NAME;
        }

        return encoding;
    }
}
