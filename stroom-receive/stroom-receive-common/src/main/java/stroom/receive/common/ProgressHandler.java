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

package stroom.receive.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import java.util.function.Consumer;

public class ProgressHandler implements Consumer<Long> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressHandler.class);

    private final String prefix;
    private long totalBytes;

    public ProgressHandler(final String prefix) {
        this.prefix = prefix + " - ";
    }

    @Override
    public void accept(final Long bytes) {
        if (LOGGER.isTraceEnabled()) {
            totalBytes += bytes;
            LOGGER.trace(() -> prefix +
                    ModelStringUtil.formatIECByteSizeString(
                            totalBytes));
        }
    }
}
