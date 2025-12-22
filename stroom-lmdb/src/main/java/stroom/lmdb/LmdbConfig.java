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

package stroom.lmdb;

import stroom.util.io.ByteSize;
import stroom.util.shared.HasPropertyPath;
import stroom.util.shared.NotInjectableConfig;

@NotInjectableConfig
public interface LmdbConfig extends HasPropertyPath {

    String LOCAL_DIR_PROP_NAME = "localDir";
    int DEFAULT_MAX_READERS = 126; // 126 is LMDB default
    ByteSize DEFAULT_MAX_STORE_SIZE = ByteSize.ofGibibytes(10);
    boolean DEFAULT_IS_READ_AHEAD_ENABLED = true;
    boolean DEFAULT_IS_READER_BLOCKED_BY_WRITER = true;

    String getLocalDir();

    int getMaxReaders();

    ByteSize getMaxStoreSize();

    boolean isReadAheadEnabled();

    boolean isReaderBlockedByWriter();
}
