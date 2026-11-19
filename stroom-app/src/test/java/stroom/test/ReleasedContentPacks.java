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

package stroom.test;

import stroom.test.common.util.test.ContentPackZip;
import stroom.test.common.util.test.ContentPackZipCollection;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.nio.file.Files;
import java.nio.file.Path;

/// The released content packs that Stroom ships, as `content-packs.json` (in the repository's root)
/// lists them: each a zip to download, by name and version.
final class ReleasedContentPacks {

    /// The definition file, relative to the repository's root.
    static final String DEFINITION_FILE = "content-packs.json";

    private ReleasedContentPacks() {
        // Static utility
    }

    /// Finds a released content pack by its name.
    ///
    /// @param definition The definition file, e.g. `content-packs.json`.
    /// @param name       The pack's name, e.g. `stroom-visualisations`.
    /// @return The pack, with the URL of its zip and its version.
    /// @throws RuntimeException If the file can't be read, or lists no pack of that name.
    static ContentPackZip find(final Path definition, final String name) {
        if (!Files.isRegularFile(definition)) {
            throw new RuntimeException(LogUtil.message("No content pack definition file at {}",
                    definition.toAbsolutePath().normalize()));
        }
        final ContentPackZipCollection packs;
        try {
            packs = JsonUtil.getMapper().readValue(definition.toFile(), ContentPackZipCollection.class);
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Unable to read the content pack definition file {}: {}",
                    definition.toAbsolutePath().normalize(), e.getMessage()), e);
        }
        return NullSafe.list(packs.getContentPacks())
                .stream()
                .filter(pack -> name.equals(pack.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(LogUtil.message("{} lists no content pack named '{}'",
                        definition.toAbsolutePath().normalize(), name)));
    }
}
