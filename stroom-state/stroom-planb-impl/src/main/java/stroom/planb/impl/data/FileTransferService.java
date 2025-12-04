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
import java.io.OutputStream;

public interface FileTransferService {

    void checkSnapshotStatus(SnapshotRequest request);

    void fetchSnapshot(SnapshotRequest request, OutputStream outputStream);

    void receivePart(long createTime,
                     long metaId,
                     String fileHash,
                     String fileName,
                     boolean synchroniseMerge,
                     InputStream inputStream) throws IOException;
}
