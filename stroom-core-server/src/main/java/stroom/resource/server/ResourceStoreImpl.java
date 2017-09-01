/*
 * Copyright 2016 Crown Copyright
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

package stroom.resource.server;

import org.springframework.stereotype.Component;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.io.FileUtil;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomStartup;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple Store that gives you 1 hour to use your temp file and then it deletes
 * it.
 */
@Component("resourceStore")
public class ResourceStoreImpl implements ResourceStore {
    private Set<ResourceKey> currentFiles = new HashSet<>();
    private Set<ResourceKey> oldFiles = new HashSet<>();
    private long sequence;
    private File tempDir = null;

    private File getTempFile() {
        if (tempDir == null) {
            tempDir = new File(FileUtil.getTempDir(), "resources");
            tempDir.mkdirs();
        }
        return tempDir;
    }

    @StroomStartup
    public void startup() {
        FileSystemUtil.deleteContents(getTempFile());
    }

    @Override
    public synchronized ResourceKey createTempFile(final String name) {
        final String fileName = new File(getTempFile(), (sequence++) + name).getAbsolutePath();
        final ResourceKey resourceKey = new ResourceKey(name, fileName);
        currentFiles.add(resourceKey);

        return resourceKey;
    }

    @Override
    public synchronized void deleteTempFile(final ResourceKey resourceKey) {
        if (currentFiles.contains(resourceKey)) {
            currentFiles.remove(resourceKey);
        }
        if (oldFiles.contains(resourceKey)) {
            oldFiles.remove(resourceKey);
        }
        final File file = new File(resourceKey.getKey());
        if (file.isFile()) {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    @Override
    public synchronized Path getTempFile(final ResourceKey resourceKey) {
        // File gone !
        if (!currentFiles.contains(resourceKey) && !oldFiles.contains(resourceKey)) {
            return null;
        }
        return Paths.get(resourceKey.getKey());
    }

    @StroomShutdown
    public void shutdown() {
        FileSystemUtil.deleteContents(getTempFile());
    }

    @StroomFrequencySchedule("1h")
    public void execute() {
        flipStore();
    }

    /**
     * Move the current files to the old files deleting the old ones.
     */
    private synchronized void flipStore() {
        final Set<ResourceKey> clonedOldFiles = new HashSet<>(oldFiles);
        clonedOldFiles.forEach(this::deleteTempFile);
        oldFiles = currentFiles;
        currentFiles = new HashSet<>();
    }
}
