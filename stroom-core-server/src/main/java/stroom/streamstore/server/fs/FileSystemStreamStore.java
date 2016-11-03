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

package stroom.streamstore.server.fs;

import java.util.Set;

import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.StreamVolume;

/**
 * <p>
 * A file system stream store.
 * </p>
 *
 * <p>
 * Stores streams in the stream store indexed by some meta data.
 * </p>
 */
public interface FileSystemStreamStore extends StreamStore {
	Set<StreamVolume> findStreamVolume(Long metaDataId);
}
