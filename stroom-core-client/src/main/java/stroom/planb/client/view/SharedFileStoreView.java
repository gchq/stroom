/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.client.view;

public interface SharedFileStoreView {

    int getShardCount();

    void setShardCount(int count);

    boolean isEnableSharedFileStore();

    void setEnableSharedFileStore(boolean enable);

    String getSharedPath();

    void setSharedPath(String sharedPath);

    /** Lock the path field (e.g. when an existing shard path must not change). */
    default void setSharedFileStorePathLocked(final boolean locked) {
    }
}
