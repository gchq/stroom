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

package stroom.util.io;

import java.io.Closeable;
import java.io.IOException;

import stroom.util.logging.StroomLogger;

public class CloseableUtil {
    static StroomLogger LOGGER = StroomLogger.getLogger(CloseableUtil.class);

    public static void closeLogAndIngoreException(Closeable... closeableList) {
        try {
            close(closeableList);
        } catch (Exception ex) {
            // Already Logged
        }
    }

    public static void close(Closeable... closeableList) throws IOException {
        Exception lastException = null;
        if (closeableList != null) {
            for (Closeable closeable : closeableList) {
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        lastException = e;
                        LOGGER.error("closeStream()", e);
                    }
                }
            }
        }
        if (lastException != null) {
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException) lastException;
            } else {
                if (lastException instanceof IOException) {
                    throw (IOException) lastException;
                }
                throw new RuntimeException(lastException);
            }
        }
    }
}
