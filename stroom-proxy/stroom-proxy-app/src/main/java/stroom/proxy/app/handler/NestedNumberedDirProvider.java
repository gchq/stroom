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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A provider of unique paths such that each directory never contains more than 999 items.
 */
public class NestedNumberedDirProvider {

    private final AtomicLong dirId;
    private final Path root;

    NestedNumberedDirProvider(final Path root) {
        this.root = Objects.requireNonNull(root);
        this.dirId = new AtomicLong(DirUtil.getMaxDirId(root));
    }

    public static NestedNumberedDirProvider create(final Path root) {
        return new NestedNumberedDirProvider(root);
    }

    /**
     * Each call to this creates a unique subdirectory of the root path.
     * <p>
     * e.g. {@code root_path/2/333/555/333555777}
     * </p>
     *
     * @throws UncheckedIOException If the new path cannot be created.
     */
    public Path createNumberedPath() {
        final Path path = DirUtil.createPath(root, dirId.incrementAndGet());
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    @Override
    public String toString() {
        return "NumberedDirProvider{" +
               "dirId=" + dirId +
               ", root=" + root +
               '}';
    }
}
