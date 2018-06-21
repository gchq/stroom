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
import stroom.data.meta.api.Stream;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.WrappedSegmentOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;

class OutputStreamProviderImpl implements OutputStreamProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutputStreamProviderImpl.class);

    private final Stream stream;
    private final NestedOutputStreamFactory rootStreamTarget;
    private final HashMap<String, OS> nestedOutputStreamMap = new HashMap<>(10);

    OutputStreamProviderImpl(final Stream stream,
                             final NestedOutputStreamFactory streamTarget) {
        this.stream = stream;
        this.rootStreamTarget = streamTarget;

        final RANestedOutputStream nestedOutputStream = new RANestedOutputStream(
                streamTarget.getOutputStream(),
                () -> streamTarget.addChild(InternalStreamTypeNames.BOUNDARY_INDEX).getOutputStream());
        final RASegmentOutputStream segmentOutputStream = new RASegmentOutputStream(
                nestedOutputStream,
                () -> streamTarget.addChild(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
        final OS os = new OS(nestedOutputStream, segmentOutputStream);
        nestedOutputStreamMap.put(null, os);
    }

    private void logDebug(String msg) {
        LOGGER.debug(msg + stream.getId());
    }

    @Override
    public SegmentOutputStream next() {
        try {
            if (LOGGER.isDebugEnabled()) {
                logDebug("next()");
            }

            final OS os = getOS(null);
            os.nestedOutputStream.putNextEntry();
            final SegmentOutputStream outputStream = os.outputStream;

            return new WrappedSegmentOutputStream(outputStream) {
                @Override
                public void close() throws IOException {
                    os.nestedOutputStream.closeEntry();
                }
            };
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public SegmentOutputStream next(final String streamTypeName) {
        try {
            if (LOGGER.isDebugEnabled()) {
                logDebug("next() - " + streamTypeName);
            }

            final OS os = getOS(streamTypeName);
            os.nestedOutputStream.putNextEntry();
            final SegmentOutputStream outputStream = os.outputStream;

            return new WrappedSegmentOutputStream(outputStream) {
                @Override
                public void close() throws IOException {
                    os.nestedOutputStream.closeEntry();
                }
            };
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private OS getOS(final String streamTypeName) {
        return nestedOutputStreamMap.computeIfAbsent(streamTypeName, k -> {
            final NestedOutputStreamFactory childTarget = rootStreamTarget.addChild(k);
            final RANestedOutputStream nestedOutputStream = new RANestedOutputStream(childTarget.getOutputStream(),
                    () -> childTarget.addChild(InternalStreamTypeNames.BOUNDARY_INDEX).getOutputStream());

            SegmentOutputStream outputStream;
//            if (!FileSystemStreamPathHelper.isStreamTypeSegment(k)) {
//                outputStream = nestedOutputStream;
//            } else {
            outputStream = new RASegmentOutputStream(nestedOutputStream,
                    () -> childTarget.addChild(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
//            }

            return new OS(nestedOutputStream, outputStream);
        });

//        if (syncWriting) {
//            final OS root = nestedOutputStreamMap.get(null);
//            // Add blank marks for the missing context files
//            final RANestedOutputStream nestedOutputStream = os.nestedOutputStream;
//            while (nestedOutputStream.getNestCount() + 1 < root.nestedOutputStream.getNestCount()) {
//                nestedOutputStream.putNextEntry();
//                nestedOutputStream.closeEntry();
//            }
//        }
//
//        return os;
    }

    @Override
    public void close() throws IOException {
        for (OS nestedOutputStream : nestedOutputStreamMap.values()) {
            nestedOutputStream.nestedOutputStream.close();
        }
    }

    private static class OS {
        RANestedOutputStream nestedOutputStream;
        SegmentOutputStream outputStream;

        OS(final RANestedOutputStream nestedOutputStream, final SegmentOutputStream outputStream) {
            this.nestedOutputStream = nestedOutputStream;
            this.outputStream = outputStream;
        }
    }
}
