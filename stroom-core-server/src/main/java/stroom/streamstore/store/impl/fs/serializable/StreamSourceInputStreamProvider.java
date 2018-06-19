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

package stroom.streamstore.store.impl.fs.serializable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Wrapper for a nested input stream.
 * <p>
 * You must call getNextEntry and closeEntry like the ZIP API.
 */
public interface StreamSourceInputStreamProvider extends Closeable {
    long getStreamCount() throws IOException;

    StreamSourceInputStream getStream(final long streamNo) throws IOException;

    RASegmentInputStream getSegmentInputStream(final long streamNo) throws IOException;
}
