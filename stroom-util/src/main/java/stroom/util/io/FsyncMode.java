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

package stroom.util.io;


/// Determines when fsync should be performed.
public enum FsyncMode {
    /// No Fsync will be performed
    DISABLED(false,
            false,
            false),
    /// Fsync will only be performed on files
    FILE_ONLY(true,
            false,
            true),
    /// Fsync will only be performed on directories
    DIR_ONLY(false,
            true,
            true),
    /// Fsync will be performed on files and directories
    ENABLED(true,
            true,
            true),
    ;

    private final boolean isEnabledForFiles;
    private final boolean isEnabledForDirs;
    private final boolean isAnyFsyncEnabled;

    FsyncMode(final boolean isEnabledForFiles,
              final boolean isEnabledForDirs,
              final boolean isAnyFsyncEnabled) {
        this.isEnabledForFiles = isEnabledForFiles;
        this.isEnabledForDirs = isEnabledForDirs;
        this.isAnyFsyncEnabled = isAnyFsyncEnabled;
    }

    /// @return `true` when file contents should be forced to durable storage.
    public boolean isEnabledForFiles() {
        return isEnabledForFiles;
    }

    /// @return `true` when directory entries should be forced to durable storage.
    public boolean isEnabledForDirs() {
        return isEnabledForDirs;
    }

    /// @return `true` when fsync is enabled for files or directories.
    public boolean isAnyFsyncEnabled() {
        return isAnyFsyncEnabled;
    }
}
