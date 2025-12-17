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

package stroom.resource.api;

import stroom.util.shared.ResourceKey;

import java.nio.file.Path;

/**
 * API to a store of generated resources. This store only last 1 hour, so it
 * should be used for temp files (e.g. GUI generated temp files).
 * <p>
 * Created resources are specific to the logged in user, so a resource
 * created by one user cannot be accessed by another user.
 * </p>
 */
public interface ResourceStore {

    String RESOURCE_STORE_PATH_PART = "/resourcestore";

    /**
     * Create a temporary file and give it a UUID.
     *
     * @param name The name of the file resource. Held mostly for logging/debug purposes.
     *             Does not have to be unique.
     */
    ResourceKey createTempFile(String name);

    /**
     * Get the temporary file associated with a {@link ResourceKey}
     */
    Path getTempFile(ResourceKey key);

    /**
     * Delete the temporary file associated with a {@link ResourceKey}
     */
    void deleteTempFile(ResourceKey key);
}
