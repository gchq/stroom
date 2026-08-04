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

package stroom.planb.impl.data;

import java.io.IOException;
import java.io.InputStream;

public interface FileTransferService {

    /**
     * Open the snapshot for the requested doc ready for streaming to the client.
     * <p>
     * This both checks that we can supply a snapshot and opens it, so that any failure happens before the
     * response status has been committed. Errors that occur once streaming has begun can't change the status,
     * so the client would otherwise see a successful response with an empty or truncated body. See gh-5689.
     *
     * @return The snapshot, which the caller must close.
     */
    InputStream openSnapshot(SnapshotRequest request);

    void receivePart(long createTime,
                     long metaId,
                     String fileHash,
                     String fileName,
                     boolean synchroniseMerge,
                     InputStream inputStream) throws IOException;
}
