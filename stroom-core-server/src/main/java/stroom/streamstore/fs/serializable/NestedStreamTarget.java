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

package stroom.streamstore.fs.serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.shared.StreamType;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class NestedStreamTarget implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NestedStreamTarget.class);

    private final StreamTarget rootStreamTarget;
    private final boolean syncWriting;
    private final HashMap<StreamType, RANestedOutputStream> nestedOutputStreamMap = new HashMap<>(10);
    private final HashMap<StreamType, OutputStream> outputStreamMap = new HashMap<>(10);

    public NestedStreamTarget(StreamTarget streamTarget, boolean syncWriting) throws IOException {
        this.rootStreamTarget = streamTarget;
        this.syncWriting = syncWriting;
        RANestedOutputStream root = new RANestedOutputStream(streamTarget);
        nestedOutputStreamMap.put(null, root);
        outputStreamMap.put(null, new RASegmentOutputStream(root,
                streamTarget.addChildStream(StreamType.SEGMENT_INDEX).getOutputStream()));

    }

    public StreamTarget getRootStreamTarget() {
        return rootStreamTarget;
    }

    private void logDebug(String msg) {
        LOGGER.debug(msg + rootStreamTarget.getStream().getId());
    }

    public void putNextEntry() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("putNextEntry()");
        }
        getRANestedOutputStream().putNextEntry();

    }

    public void closeEntry() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("closeEntry()");
        }
        getRANestedOutputStream().closeEntry();

    }

    public void putNextEntry(StreamType streamType) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("putNextEntry() - " + streamType);
        }
        getRANestedOutputStream(streamType).putNextEntry();
    }

    public void closeEntry(StreamType streamType) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("closeEntry() - " + streamType);
        }
        getRANestedOutputStream(streamType).closeEntry();
    }

    private RANestedOutputStream getRANestedOutputStream() throws IOException {
        return nestedOutputStreamMap.get(null);
    }

    private RANestedOutputStream getRANestedOutputStream(StreamType streamType) throws IOException {
        RANestedOutputStream root = nestedOutputStreamMap.get(null);
        RANestedOutputStream child = nestedOutputStreamMap.get(streamType);
        if (child == null) {
            StreamTarget childTarget = rootStreamTarget.addChildStream(streamType);
            child = new RANestedOutputStream(childTarget);
            nestedOutputStreamMap.put(streamType, child);
        }

        if (syncWriting) {
            // Add blank marks for the missing context files
            while (child.getNestCount() + 1 < root.getNestCount()) {
                child.putNextEntry();
                child.closeEntry();
            }
        }

        return child;
    }

    public OutputStream getOutputStream() throws IOException {
        return getOutputStream(null);
    }

    public OutputStream getOutputStream(StreamType streamType) throws IOException {
        OutputStream outputStream = outputStreamMap.get(streamType);
        if (outputStream == null) {
            if (!streamType.isStreamTypeSegment()) {
                outputStream = getRANestedOutputStream(streamType);
            } else {
                RANestedOutputStream nestedOutputStream = getRANestedOutputStream(streamType);
                StreamTarget childTarget = rootStreamTarget.getChildStream(streamType);
                outputStream = new RASegmentOutputStream(nestedOutputStream,
                        childTarget.addChildStream(StreamType.SEGMENT_INDEX).getOutputStream());
                outputStreamMap.put(streamType, outputStream);
            }
        }
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        for (RANestedOutputStream nestedOutputStream : nestedOutputStreamMap.values()) {
            nestedOutputStream.close();
        }
    }

}
