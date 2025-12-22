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

package stroom.data.store.api;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream that works like a zip input stream in that you can iterate over nested
 * items with in the main stream.
 */
public abstract class NestedInputStream extends InputStream {

    /**
     * @return number of nested entries
     */
    public abstract long getEntryCount() throws IOException;

    /**
     * Tries to get a specific entry number.
     *
     * @return true if we have been able to get the requested entry number.
     */
    public abstract boolean getEntry(long entryNo) throws IOException;

    /**
     * @return true if we have another entry to read
     */
    public abstract boolean getNextEntry() throws IOException;

    /**
     * You must call this to before you try and get the next entry.
     */
    public abstract void closeEntry() throws IOException;
}
