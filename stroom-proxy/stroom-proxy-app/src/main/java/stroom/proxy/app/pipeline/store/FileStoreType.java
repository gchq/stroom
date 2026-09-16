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

package stroom.proxy.app.pipeline.store;

import stroom.proxy.app.handler.Durability;

/**
 * What a file store is backed by. The type says what the storage is, so the configuration can be
 * checked against the deployment mode, the durability default can follow it, and age-based cleanup
 * runs only where it is the mechanism.
 */
public enum FileStoreType {
    /**
     * A directory on this node's own disk. Local mode only; one process is the only writer, so
     * transient state and orphans are cleared at start-up.
     */
    LOCAL_FILESYSTEM,

    /**
     * A directory on a mount every node sees - Ceph, EFS, NFS. Shared mode only. The operator
     * asserts the mount is shared by choosing this type; the proxy cannot tell from a path. Residue
     * is cleared by age, because the node that left it may never come back.
     */
    SHARED_FILESYSTEM,

    /**
     * An S3 bucket, or an S3-compatible store. Shared mode only. Residue is cleared by a lifecycle
     * rule the operator configures.
     */
    S3;

    public boolean isFilesystem() {
        return this != S3;
    }

    public boolean isShared() {
        return this != LOCAL_FILESYSTEM;
    }

    /**
     * What a commit forces when the operator has not said. Local disk is forced in full because
     * nothing else guarantees the ordering; a shared mount is left to its own semantics, which a
     * local fsync says nothing about; an object store's acknowledgement is the durability point.
     */
    public Durability defaultDurability() {
        return this == LOCAL_FILESYSTEM
                ? Durability.FULL
                : Durability.FILESYSTEM;
    }
}
