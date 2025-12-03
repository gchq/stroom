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

import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.meta.shared.Meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputStreamProviderImpl implements OutputStreamProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputStreamProviderImpl.class);

    private final Meta meta;
    private final SegmentOutputStreamProviderFactory factory;
    private final SegmentOutputStreamProvider root;
    private final long index;

    public OutputStreamProviderImpl(final Meta meta,
                                    final SegmentOutputStreamProviderFactory factory,
                                    final long index) {
        this.meta = meta;
        this.factory = factory;
        this.index = index;
        root = factory.getSegmentOutputStreamProvider(null);
    }

    private void logDebug(final String msg) {
        LOGGER.debug(msg + meta.getId());
    }

    @Override
    public SegmentOutputStream get() {
        if (LOGGER.isDebugEnabled()) {
            logDebug("get()");
        }

        return root.get(index);
    }

    @Override
    public SegmentOutputStream get(final String streamTypeName) {
        if (streamTypeName == null) {
            return get();
        }

        if (LOGGER.isDebugEnabled()) {
            logDebug("get() - " + streamTypeName);
        }

        final SegmentOutputStreamProvider segmentOutputStreamProvider = factory.getSegmentOutputStreamProvider(
                streamTypeName);
        if (segmentOutputStreamProvider == null) {
            return null;
        }
        return segmentOutputStreamProvider.get(index);
    }

    @Override
    public void close() {
//        for (SegmentOutputStreamProvider nestedOutputStream : nestedOutputStreamMap.values()) {
//            nestedOutputStream.nestedOutputStream.close();
//        }
    }
}
