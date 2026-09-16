/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.store.filesystem;

import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.pipeline.store.AbstractFileStoreContractTest;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreType;

import java.nio.file.Path;
import java.time.Duration;

class TestSharedFilesystemFileStoreContract extends AbstractFileStoreContractTest {

    @Override
    protected FileStore createFileStore(final String storeName, final Path testRoot) {
        return new FilesystemFileStore(
                storeName,
                testRoot.resolve(storeName),
                FileStoreType.SHARED_FILESYSTEM,
                Durability.FILESYSTEM,
                Duration.ofDays(1));
    }
}
