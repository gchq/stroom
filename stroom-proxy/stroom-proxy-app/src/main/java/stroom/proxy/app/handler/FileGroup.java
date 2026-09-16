/*
 * Copyright 2023 Crown Copyright
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

import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileGroup {

    static final String BASE_FILENAME = "proxy";
    static final String META_EXTENSION = "meta";
    static final String ZIP_EXTENSION = "zip";
    static final String ENTRIES_EXTENSION = "entries";
    /** Written beside a group by a give-up, saying why the proxy stopped trying to forward it. */
    public static final String ERROR_LOG_FILE_NAME = "error.log";
    static final String META_FILE = BASE_FILENAME + "." + META_EXTENSION;
    static final String ZIP_FILE = BASE_FILENAME + "." + ZIP_EXTENSION;
    static final String ENTRIES_FILE = BASE_FILENAME + "." + ENTRIES_EXTENSION;

    private final Path parentDir;
    private final Path zip;
    private final Path meta;
    private final Path entries;

    public FileGroup(final Path parentDir) {
        this.parentDir = parentDir;
        this.zip = parentDir.resolve(ZIP_FILE);
        this.meta = parentDir.resolve(META_FILE);
        this.entries = parentDir.resolve(ENTRIES_FILE);
    }

    /**
     * @return The path to the .zip file
     */
    public Path getZip() {
        return zip;
    }

    /**
     * @return The path to the .meta file
     */
    public Path getMeta() {
        return meta;
    }

    /**
     * @return The .entries file containing the {@link ZipEntryGroup}s, serialised as
     * JSON with one per line.
     */
    public Path getEntries() {
        return entries;
    }

    /**
     * @return The parent dir that contains the meta, zip and entries files.
     */
    public Path getParentDir() {
        return parentDir;
    }

    /**
     * Check that this group is complete: a directory holding {@code proxy.meta}, {@code proxy.zip}
     * and {@code proxy.entries}, all regular files.
     * <p>
     * Only the forward stage checked this, in a private copy, so a group that
     * had lost a member travelled the whole pipeline before anything noticed. The failure it produced
     * was far from its cause, and by then the input that could have been re-processed had been
     * acknowledged and deleted.
     * </p>
     * <p>
     * <strong>Absence of the directory is a different thing entirely</strong> and is not checked
     * here: under R12 (§2.6) an input that is not there means the work was already done, and
     * {@code resolve()} reports that itself (R10, §3.3). This is about a group that <em>is</em> there
     * and is not whole - which no rule makes acceptable.
     * </p>
     *
     * @param context Named in the exception, so a log line says which stage rejected what.
     * @throws IOException If the directory or any member is missing.
     */
    public void requireComplete(final String context) throws IOException {
        if (!Files.isDirectory(parentDir)) {
            throw new IOException(LogUtil.message(
                    "{}: '{}' is not a directory, so it cannot be a file group", context, parentDir));
        }
        requireRegularFile(context, getMeta(), "meta");
        requireRegularFile(context, getZip(), "zip");
        requireRegularFile(context, getEntries(), "entries");
    }

    private static void requireRegularFile(final String context,
                                           final Path path,
                                           final String description) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(LogUtil.message(
                    "{}: file group is missing its {} file '{}'", context, description, path));
        }
    }

    /**
     * @return All items in the file group
     */
    public List<Path> items() {
        return List.of(zip, meta, entries);
    }

    @Override
    public String toString() {
        return parentDir.toString();
    }
}
