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

package stroom.gitrepo.impl;

public interface GitRepoDao {

    /**
     * Stores the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that this matches.
     * @param hash The Git commit hash that we've obtained from Git.
     */
    void storeHash(String uuid, String hash);

    /**
     * Returns the Git commit hash for a given GitRepoDoc UUID.
     * @param uuid The UUID of the doc that we want the hash for.
     * @return The Git commit hash, or null.
     */
    String getHash(String uuid);

}
