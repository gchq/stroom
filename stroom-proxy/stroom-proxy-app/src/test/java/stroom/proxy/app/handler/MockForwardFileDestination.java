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

package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class MockForwardFileDestination implements ForwardFileDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MockForwardFileDestination.class);

    private final Path storeDir;
    private final List<Path> addedPaths = new ArrayList<>();

    private final AtomicLong writeId = new AtomicLong();
    private volatile CountDownLatch countDownLatch;

    public MockForwardFileDestination() {
        try {
            this.storeDir = Files.createTempDirectory("test");
            LOGGER.info("Creating {} with storeDir {}", this.getClass().getSimpleName(), storeDir);

            // Initialise the store id.
            final long maxId = DirUtil.getMaxDirId(storeDir);
            writeId.set(maxId);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public MockForwardFileDestination(final Path storeDir) {
        this.storeDir = storeDir;

        // Initialise the store id.
        final long maxId = DirUtil.getMaxDirId(storeDir);
        writeId.set(maxId);
    }

    public void setCountDownLatch(final CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void add(final Path sourceDir) {
        // Record the sequence id for future use.
        final long commitId = writeId.incrementAndGet();
        final Path targetDir = DirUtil.createPath(storeDir, commitId);
        try {
            move(sourceDir, targetDir);
            addedPaths.add(targetDir);
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getName() {
        return "Mock File Destination";
    }

    @Override
    public DestinationType getDestinationType() {
        return DestinationType.FILE;
    }

    @Override
    public String getDestinationDescription() {
        return "Mock File Destination";
    }

    private void move(final Path source, final Path target) throws IOException {
        try {
            Files.move(source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final NoSuchFileException e) {
            DirUtil.ensureDirExists(target.getParent());
            Files.move(source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
        }
    }

    public Path getStoreDir() {
        return storeDir;
    }

    public List<Path> getAddedPaths() {
        return addedPaths;
    }
}
