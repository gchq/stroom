/*
 * Copyright 2025 Crown Copyright
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

package stroom.planb.impl.data;

import stroom.node.api.NodeCallException;

import java.nio.file.Path;
import java.time.Instant;

public interface FileTransferClient {

    void storePart(FileDescriptor fileDescriptor,
                   Path path,
                   boolean synchroniseMerge);

    /**
     * Fetch a snapshot from the named node into the supplied directory.
     *
     * @return The data time of the fetched snapshot.
     * @throws NotModifiedException If the node confirms the snapshot the caller already holds is current. This is
     *                              an answer, not a failure, so it must reach the caller with its type intact.
     * @throws NodeCallException    If the node could not be reached, i.e. it gave no answer, so another node is
     *                              worth trying.
     */
    Instant fetchSnapshot(String nodeName,
                          SnapshotRequest request,
                          Path snapshotDir);
}
