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
import stroom.data.store.api.NestedStreamTarget;
import stroom.data.store.api.StreamTarget;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

class RANestedStreamTarget implements NestedStreamTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(RANestedStreamTarget.class);

    private final StreamTarget rootStreamTarget;
    private final boolean syncWriting;
    private final HashMap<String, RANestedOutputStream> nestedOutputStreamMap = new HashMap<>(10);
    private final HashMap<String, OutputStream> outputStreamMap = new HashMap<>(10);

    RANestedStreamTarget(StreamTarget streamTarget, boolean syncWriting) {
        this.rootStreamTarget = streamTarget;
        this.syncWriting = syncWriting;
        RANestedOutputStream root = new RANestedOutputStream(
                streamTarget.getOutputStream(),
                streamTarget.addChildStream(InternalStreamTypeNames.BOUNDARY_INDEX).getOutputStream());
        nestedOutputStreamMap.put(null, root);
        outputStreamMap.put(null, new RASegmentOutputStream(root,
                streamTarget.addChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream()));

    }

    private void logDebug(String msg) {
        LOGGER.debug(msg + rootStreamTarget.getStream().getId());
    }

    @Override
    public void putNextEntry() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("putNextEntry()");
        }
        getRANestedOutputStream().putNextEntry();

    }

    @Override
    public void closeEntry() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("closeEntry()");
        }
        getRANestedOutputStream().closeEntry();

    }

    @Override
    public void putNextEntry(String streamTypeName) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("putNextEntry() - " + streamTypeName);
        }
        getRANestedOutputStream(streamTypeName).putNextEntry();
    }

    @Override
    public void closeEntry(String streamType) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            logDebug("closeEntry() - " + streamType);
        }
        getRANestedOutputStream(streamType).closeEntry();
    }

    private RANestedOutputStream getRANestedOutputStream() {
        return nestedOutputStreamMap.get(null);
    }

    private RANestedOutputStream getRANestedOutputStream(String streamTypeName) throws IOException {
        RANestedOutputStream root = nestedOutputStreamMap.get(null);
        RANestedOutputStream child = nestedOutputStreamMap.get(streamTypeName);
        if (child == null) {
            StreamTarget childTarget = rootStreamTarget.addChildStream(streamTypeName);
            child = new RANestedOutputStream(childTarget.getOutputStream(),
                    childTarget.addChildStream(InternalStreamTypeNames.BOUNDARY_INDEX).getOutputStream());
            nestedOutputStreamMap.put(streamTypeName, child);
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

    @Override
    public OutputStream getOutputStream() throws IOException {
        return getOutputStream(null);
    }

    @Override
    public OutputStream getOutputStream(String streamType) throws IOException {
        OutputStream outputStream = outputStreamMap.get(streamType);
        if (outputStream == null) {
            if (!FileSystemStreamPathHelper.isStreamTypeSegment(streamType)) {
                outputStream = getRANestedOutputStream(streamType);
            } else {
                RANestedOutputStream nestedOutputStream = getRANestedOutputStream(streamType);
                StreamTarget childTarget = rootStreamTarget.getChildStream(streamType);
                outputStream = new RASegmentOutputStream(nestedOutputStream,
                        childTarget.addChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
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
