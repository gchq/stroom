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

package stroom.proxy.app.pipeline.stress;

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link FileStore} that wraps a real store and injects faults around it.
 * <p>
 * {@link FaultPoint#STORE_COMMIT_AFTER} is the one worth understanding: the
 * commit lands, so the data is durable and visible, but the writing stage
 * believes it failed and will write it again. The first copy is then an orphan -
 * committed, referenced by nothing, cleaned up by no one. The stress scenarios
 * assert that orphans cost disk and nothing else; in particular that they are
 * never counted as delivered and never resurrect as duplicate deliveries.
 * </p>
 */
public class FaultInjectingFileStore implements FileStore {

    private final FileStore delegate;
    private final FaultPolicy faultPolicy;

    public FaultInjectingFileStore(final FileStore delegate,
                                   final FaultPolicy faultPolicy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faultPolicy = Objects.requireNonNull(faultPolicy, "faultPolicy");
    }

    public FileStore getDelegate() {
        return delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public FileStoreWrite newWrite() throws IOException {
        faultPolicy.maybeFail(FaultPoint.STORE_NEW_WRITE);
        faultPolicy.maybeDelay();
        return new FaultInjectingWrite(delegate.newWrite(), faultPolicy);
    }

    @Override
    public FileStoreWrite newDeterministicWrite(final String fileGroupId) throws IOException {
        faultPolicy.maybeFail(FaultPoint.STORE_NEW_WRITE);
        faultPolicy.maybeDelay();
        return new FaultInjectingWrite(delegate.newDeterministicWrite(fileGroupId), faultPolicy);
    }

    @Override
    public Path resolve(final FileStoreLocation location) throws IOException {
        faultPolicy.maybeFail(FaultPoint.STORE_RESOLVE);
        return delegate.resolve(location);
    }

    @Override
    public void delete(final FileStoreLocation location) throws IOException {
        // Failing before the delete leaves the consumed input in place. The
        // producing stage has already published, so the input is now unreferenced
        // input rather than lost data.
        faultPolicy.maybeFail(FaultPoint.STORE_DELETE);
        faultPolicy.maybeDelay();
        delegate.delete(location);
    }

    @Override
    public HealthCheck.Result healthCheck() {
        return delegate.healthCheck();
    }

    // -------------------------------------------------------------------------

    private static final class FaultInjectingWrite implements FileStoreWrite {

        private final FileStoreWrite delegate;
        private final FaultPolicy faultPolicy;

        private FaultInjectingWrite(final FileStoreWrite delegate,
                                    final FaultPolicy faultPolicy) {
            this.delegate = delegate;
            this.faultPolicy = faultPolicy;
        }

        @Override
        public Path getPath() {
            return delegate.getPath();
        }

        @Override
        public FileStoreLocation commit() throws IOException {
            faultPolicy.maybeFail(FaultPoint.STORE_COMMIT);
            faultPolicy.maybeDelay();

            final FileStoreLocation location = delegate.commit();

            // Committed and durable, but the caller is told otherwise: an orphan.
            faultPolicy.maybeFail(FaultPoint.STORE_COMMIT_AFTER);

            return location;
        }

        @Override
        public boolean isCommitted() {
            return delegate.isCommitted();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
