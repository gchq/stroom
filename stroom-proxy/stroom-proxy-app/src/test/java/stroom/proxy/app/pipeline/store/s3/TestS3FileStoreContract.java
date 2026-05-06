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

package stroom.proxy.app.pipeline.store.s3;

import stroom.proxy.app.pipeline.store.AbstractFileStoreContractTest;
import stroom.proxy.app.pipeline.store.FileStore;

import java.nio.file.Path;

/**
 * Runs the {@link AbstractFileStoreContractTest} suite against
 * {@link S3FileStore} using a {@link StubS3Client}.
 */
class TestS3FileStoreContract extends AbstractFileStoreContractTest {

    private static final String BUCKET = "contract-bucket";

    @Override
    protected FileStore createFileStore(final String storeName, final Path testRoot) {
        final StubS3Client stub = new StubS3Client(testRoot.resolve("s3-backing"));
        return new S3FileStore(
                storeName,
                BUCKET,
                storeName + "/",
                stub,
                testRoot.resolve("local-root"),
                "contract-writer");
    }
}
