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

package stroom.util.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class CloseableUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloseableUtil.class);

    public static void closeLogAndIgnoreException(final Closeable... closeableList) {
        try {
            close(closeableList);
        } catch (final IOException e) {
            LOGGER.trace(e.getMessage(), e);
        }
    }

    public static void close(final Closeable... closeableList) throws IOException {
        IOException lastException = null;
        if (closeableList != null) {
            for (final Closeable closeable : closeableList) {
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (final IOException e) {
                        lastException = e;
                        LOGGER.error("closeStream()", e);
                    }
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }
}
