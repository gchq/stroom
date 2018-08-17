/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store;

import stroom.node.shared.VolumeEntity;

/**
 * Low level API to manage the stream store.
 */
public interface StreamMaintenanceService {
    /**
     * Scan a directory deleting old stuff and building an index of what is
     * there. Return back a list of sub dir's to nest into.
     */
    ScanVolumePathResult scanVolumePath(VolumeEntity volume, boolean doDelete, String path, long oldFileAge);

}
