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

package stroom.data.store.impl.fs.s3v2;


import stroom.aws.s3.impl.S3Manager;
import stroom.meta.shared.Meta;
import stroom.util.io.WrappedInputStream;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Gets each frame directly from S3 using a GET with byte range.
 * Intended for use when only one segment or only a small percentage of the stream is required.
 */
public class S3DirectFrameSupplierImpl implements ZstdFrameSupplier {

    private final S3Manager s3Manager;
    private final Meta meta;
    private final String childStreamType;
    private final String keyNameTemplate;

    private InputStream currentInputStream = null;
    private FrameLocation currentFrameLocation = null;

    public S3DirectFrameSupplierImpl(final S3Manager s3Manager,
                                     final Meta meta,
                                     final String childStreamType,
                                     final String keyNameTemplate) {
        this.s3Manager = s3Manager;
        this.meta = meta;
        this.childStreamType = childStreamType;
        this.keyNameTemplate = keyNameTemplate;
    }

    @Override
    public InputStream getFrameInputStream(final FrameLocation frameLocation) throws IOException {
        Objects.requireNonNull(frameLocation);
        if (currentInputStream != null) {
            throw new IllegalStateException("An responseInputStream is already open for frameLocation "
                                            + frameLocation);
        }

        // TODO When we want the whole stream we want a different approach
        final ResponseInputStream<GetObjectResponse> responseInputStream = s3Manager.getByteRange(
                meta,
                childStreamType,
                keyNameTemplate,
                frameLocation.asRange());

        final WrappedInputStream wrappedInputStream = new WrappedInputStream(responseInputStream) {
            @Override
            public void close() throws IOException {
                super.close();
                currentInputStream = null;
                currentFrameLocation = null;
            }
        };
        this.currentInputStream = wrappedInputStream;
        this.currentFrameLocation = frameLocation;
        return wrappedInputStream;
    }

    @Override
    public void close() throws Exception {
        if (currentInputStream != null) {
            currentInputStream.close();
        }
    }
}
