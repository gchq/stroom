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

package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.WrappedSegmentInputStream;
import stroom.meta.shared.Meta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;

class InputStreamProviderImpl implements InputStreamProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamProviderImpl.class);

    private final Meta meta;
    private final NestedInputStreamFactory nestedInputStreamFactory;
    private final SegmentInputStreamProvider root;
    private final HashMap<String, SegmentInputStreamProvider> nestedOutputStreamMap = new HashMap<>(10);
    private final long index;

    InputStreamProviderImpl(final Meta meta,
                            final NestedInputStreamFactory nestedInputStreamFactory,
                            final long index) {
        this.meta = meta;
        this.nestedInputStreamFactory = nestedInputStreamFactory;
        this.index = index;
        root = new SegmentInputStreamProvider(nestedInputStreamFactory, null);
    }

    private void logDebug(String msg) {
        LOGGER.debug(msg + meta.getId());
    }

    @Override
    public SegmentInputStream get() {
        if (LOGGER.isDebugEnabled()) {
            logDebug("get()");
        }

        return root.getInputStream(index);
    }

    @Override
    public SegmentInputStream get(final String streamTypeName) {
        if (LOGGER.isDebugEnabled()) {
            logDebug("get() - " + streamTypeName);
        }

        return getSegmentInputStreamProvider(streamTypeName).getInputStream(index);
    }

    private SegmentInputStreamProvider getSegmentInputStreamProvider(final String streamTypeName) {
        return nestedOutputStreamMap.computeIfAbsent(streamTypeName, k -> {
            final NestedInputStreamFactory childNestedInputStreamFactory = nestedInputStreamFactory.getChild(k);
            return new SegmentInputStreamProvider(childNestedInputStreamFactory, k);
        });
    }

    @Override
    public void close() throws IOException {
        for (SegmentInputStreamProvider nestedOutputStream : nestedOutputStreamMap.values()) {
            nestedOutputStream.nestedInputStream.close();
        }
    }

    private static class SegmentInputStreamProvider {
        private long index = -1;
        private final String dataTypeName;
        private final RANestedInputStream nestedInputStream;
        private final SegmentInputStream inputStream;

        SegmentInputStreamProvider(final NestedInputStreamFactory nestedInputStreamFactory, final String dataTypeName) {
            this.dataTypeName = dataTypeName;

            nestedInputStream = new RANestedInputStream(nestedInputStreamFactory.getInputStream(),
                    () -> nestedInputStreamFactory.getChild(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream());
            inputStream = new RASegmentInputStream(nestedInputStream,
                    () -> nestedInputStreamFactory.getChild(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream());
        }

        public SegmentInputStream getInputStream(final long index) {
            try {
                if (this.index >= index) {
                    throw new IOException("Input stream already provided for index " + index);
                }

                this.index++;
                nestedInputStream.getEntry(index);

                return new WrappedSegmentInputStream(inputStream) {
                    @Override
                    public void close() throws IOException {
                        nestedInputStream.closeEntry();
                    }
                };
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
