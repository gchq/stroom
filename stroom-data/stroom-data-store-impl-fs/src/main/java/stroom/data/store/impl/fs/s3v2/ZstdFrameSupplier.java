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


import java.io.IOException;
import java.io.InputStream;

public interface ZstdFrameSupplier extends AutoCloseable {

    /**
     * Get an {@link InputStream} for the supplied {@link FrameLocation}.
     * The {@link InputStream} must be closed before calling this method again.
     */
    // TODO considering also passing in a list of FrameLocations so that the ZstdFrameSupplier can
    //  asynchronously pre-fetch the frames in the list so that they are immediately available on
    //  the next call to getFrameInputStream with one of those FrameLocations. This is to combat
    //  the potential latency in S3 GETs.
    InputStream getFrameInputStream(final FrameLocation frameLocation) throws IOException;
}
