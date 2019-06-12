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

package stroom.proxy.repo;

import stroom.util.config.StroomProperties;

/**
 * Buffer held in thread scope for performance reasons.
 */
public class ProxyBufferSizeUtil {
    private ProxyBufferSizeUtil() {
    }

    /**
     * Same size as JDK's Buffered Output Stream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static volatile int value = DEFAULT_BUFFER_SIZE;
    private static volatile long lastTime;

    public static int get() {
        final long now = System.currentTimeMillis();
        if (lastTime < now - 10000) {
            value = StroomProperties.getIntProperty("stroom.proxyBufferSize", DEFAULT_BUFFER_SIZE);
            lastTime = now;
        }
        return value;
    }
}
