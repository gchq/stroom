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

package stroom.datafeed;

import stroom.properties.api.PropertyService;

import javax.inject.Inject;

/**
 * Buffer held in thread scope for performance reasons.
 */
public class BufferFactory {
    private final PropertyService propertyService;

    @Inject
    public BufferFactory(final PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    /**
     * Same size as JDK's Buffered Output Stream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

//    private static ThreadLocal<byte[]> threadLocalBuffer = ThreadLocal.withInitial(() -> {
//        int size = StroomProperties.getIntProperty("stroom.bufferSize", DEFAULT_BUFFER_SIZE);
//        return new byte[size];
//    });

    public byte[] create() {
        int size = propertyService.getIntProperty("stroom.bufferSize", DEFAULT_BUFFER_SIZE);
        return new byte[size];

//        return threadLocalBuffer.get();
    }
}