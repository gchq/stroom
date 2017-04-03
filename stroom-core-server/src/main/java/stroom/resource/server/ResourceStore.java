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

package stroom.resource.server;

import java.io.File;
import java.nio.file.Path;

import stroom.util.shared.ResourceKey;

/**
 * API to a store of generated resources. This store only last 1 hour so it
 * should be used for temp files (e.g. GUI generated temp files0
 */
public interface ResourceStore {
    /**
     * Create a temporary file and give it a string key.
     */
    ResourceKey createTempFile(final String name);

    /**
     * Get a temporary file
     */
    Path getTempFile(ResourceKey key);

    /**
     * Delete a temporary file.
     */
    void deleteTempFile(ResourceKey key);
}
