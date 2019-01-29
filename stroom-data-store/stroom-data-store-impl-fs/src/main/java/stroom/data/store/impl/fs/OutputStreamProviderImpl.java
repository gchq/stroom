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
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.WrappedSegmentOutputStream;
import stroom.meta.shared.Meta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;

class OutputStreamProviderImpl implements OutputStreamProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutputStreamProviderImpl.class);

    private final Meta meta;
    private final NestedOutputStreamFactory nestedOutputStreamFactory;
    private final SegmentOutputStreamProvider root;
    private final HashMap<String, SegmentOutputStreamProvider> nestedOutputStreamMap = new HashMap<>(10);
    private final long index;

    OutputStreamProviderImpl(final Meta meta,
                             final NestedOutputStreamFactory nestedOutputStreamFactory,
                             final long index) {
        this.meta = meta;
        this.nestedOutputStreamFactory = nestedOutputStreamFactory;
        this.index = index;
        root = new SegmentOutputStreamProvider(nestedOutputStreamFactory, null);
    }

    private void logDebug(String msg) {
        LOGGER.debug(msg + meta.getId());
    }

    @Override
    public SegmentOutputStream get() {
        if (LOGGER.isDebugEnabled()) {
            logDebug("get()");
        }

        return root.getOutputStream(index);
    }

    @Override
    public SegmentOutputStream get(final String streamTypeName) {
        if (LOGGER.isDebugEnabled()) {
            logDebug("get() - " + streamTypeName);
        }

        return getSegmentOutputStreamProvider(streamTypeName).getOutputStream(index);
    }

    private SegmentOutputStreamProvider getSegmentOutputStreamProvider(final String streamTypeName) {
        return nestedOutputStreamMap.computeIfAbsent(streamTypeName, k -> {
            final NestedOutputStreamFactory childNestedOutputStreamFactory = nestedOutputStreamFactory.addChild(k);
            return new SegmentOutputStreamProvider(childNestedOutputStreamFactory, k);
        });
    }

    @Override
    public void close() throws IOException {
        for (SegmentOutputStreamProvider nestedOutputStream : nestedOutputStreamMap.values()) {
            nestedOutputStream.nestedOutputStream.close();
        }
    }

    private static class SegmentOutputStreamProvider {
        private long index = -1;
        private final String dataTypeName;
        private final RANestedOutputStream nestedOutputStream;
        private final SegmentOutputStream outputStream;

        SegmentOutputStreamProvider(final NestedOutputStreamFactory nestedOutputStreamFactory, final String dataTypeName) {
            this.dataTypeName = dataTypeName;

            nestedOutputStream = new RANestedOutputStream(nestedOutputStreamFactory.getOutputStream(),
                    () -> nestedOutputStreamFactory.addChild(InternalStreamTypeNames.BOUNDARY_INDEX).getOutputStream());
            outputStream = new RASegmentOutputStream(nestedOutputStream,
                    () -> nestedOutputStreamFactory.addChild(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
        }

        public SegmentOutputStream getOutputStream(final long index) {
            try {
                if (this.index >= index) {
                    throw new IOException("Output stream already provided for index " + index);
                }

                // Move up to the right index if this OS is behind, i.e. it hasn't been requested for a certain data type before.
                while (this.index < index - 1) {
                    LOGGER.debug("Fast forwarding for " + dataTypeName);
                    this.index++;
                    nestedOutputStream.putNextEntry();
                    nestedOutputStream.closeEntry();
                }

                this.index++;
                nestedOutputStream.putNextEntry();

                return new WrappedSegmentOutputStream(outputStream) {
                    @Override
                    public void close() throws IOException {
                        nestedOutputStream.closeEntry();
                    }
                };
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
