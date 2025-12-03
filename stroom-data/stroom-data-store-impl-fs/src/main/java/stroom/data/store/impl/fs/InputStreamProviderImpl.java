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

package stroom.data.store.impl.fs;

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.meta.shared.Meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class InputStreamProviderImpl implements InputStreamProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamProviderImpl.class);

    private final Meta meta;
    private final SegmentInputStreamProviderFactory factory;
    private final SegmentInputStreamProvider root;
    private final long index;

    public InputStreamProviderImpl(final Meta meta,
                                   final SegmentInputStreamProviderFactory factory,
                                   final long index) {
        this.meta = meta;
        this.factory = factory;
        this.index = index;
        root = factory.getSegmentInputStreamProvider(null);
    }

    private void logDebug(final String msg) {
        LOGGER.debug(msg + meta.getId());
    }

    @Override
    public SegmentInputStream get() {
        if (LOGGER.isDebugEnabled()) {
            logDebug("get()");
        }

        return root.get(index);
    }

    @Override
    public SegmentInputStream get(final String streamTypeName) {
        if (streamTypeName == null) {
            return get();
        }

        if (LOGGER.isDebugEnabled()) {
            logDebug("get() - " + streamTypeName);
        }

        final SegmentInputStreamProvider segmentInputStreamProvider = factory.getSegmentInputStreamProvider(
                streamTypeName);
        if (segmentInputStreamProvider == null) {
            return null;
        }
        return segmentInputStreamProvider.get(index);
    }

    @Override
    public void close() {
//        for (SegmentInputStreamProvider segmentInputStreamProvider : providerMap.values()) {
//            segmentInputStreamProvider.close();
//        }
    }

    @Override
    public Set<String> getChildTypes() {
        return factory.getChildTypes();
    }
}
