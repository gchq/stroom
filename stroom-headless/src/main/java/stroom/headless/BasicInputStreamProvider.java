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

package stroom.headless;

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class BasicInputStreamProvider implements InputStreamProvider {

    private final Map<String, SegmentInputStream> inputStreamMap = new HashMap<>();

    @Override
    public SegmentInputStream get() {
        return inputStreamMap.get(null);
    }

    @Override
    public SegmentInputStream get(final String streamType) {
        return inputStreamMap.get(streamType);
    }

    @Override
    public Set<String> getChildTypes() {
        return inputStreamMap.keySet();
    }

    public void put(final String streamType, final InputStream inputStream, final int size) {
        inputStreamMap.put(streamType, new SingleSegmentInputStreamImpl(inputStream, size));
    }

    @Override
    public void close() {
        inputStreamMap.forEach((k, v) -> {
            try {
                v.close();
            } catch (final IOException e) {
                // Ignore.
            }
        });
    }
}
