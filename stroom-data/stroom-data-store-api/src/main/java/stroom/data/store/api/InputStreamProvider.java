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

import java.io.Closeable;
import java.util.Set;

public interface InputStreamProvider extends Closeable {

    /**
     * @return The {@link SegmentInputStream} for the main data stream
     */
    SegmentInputStream get();

    /**
     * @return The {@link SegmentInputStream} for the specified child streamTypeName, i.e. types
     * as defined in {@link stroom.data.shared.StreamTypeNames}. If streamTypeName is null,
     * this is the same as calling {@link InputStreamProvider#get()}.
     */
    SegmentInputStream get(String streamTypeName);

    /**
     * @return The set of child stream type names in this stream (part of a stream).
     * I.e. names as defined in {@link stroom.data.shared.StreamTypeNames}.
     * May be empty if the stream has no child streams.
     */
    Set<String> getChildTypes();
}
