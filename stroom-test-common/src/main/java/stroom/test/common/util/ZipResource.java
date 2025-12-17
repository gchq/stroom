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

package stroom.test.common.util;

import stroom.util.io.StreamUtil;
import stroom.util.zip.ZipUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A TestRule to compile folders into zips of the same name.
 * <p>
 * Also provides helpful methods for getting streams for the zipz.
 * <p>
 * Deletes the zips afterwards.
 * <p>
 * E.g. the following will create a zip file called ZIP_FOLDER.zip in files,
 * containing everything in files/ZIP_FOLDER:
 * <p>
 * <pre>
 * &#64;ClassRule
 * public static ZipResource bomBlank = new ZipResource("files/ZIP_FOLDER");
 * </pre>
 */
public class ZipResource {

    private static final String RESOURCES = "src/test/resources/";
    private static final String ZIP_EXTENSION = ".zip";

    private final String folderToZip;
    private Path zip;

    public ZipResource(final String folderToZip) {
        this.folderToZip = folderToZip;
    }

    public Path getPath() {
        return Paths.get(RESOURCES + folderToZip + ZIP_EXTENSION);
    }

    public InputStream getStream() throws IOException {
        return Files.newInputStream(getPath());
    }

    public byte[] getBytes() throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            StreamUtil.streamToStream(getStream(), baos);
            baos.flush();
            return baos.toByteArray();
        }
    }

    public void before() throws IOException {
        final Path inputDir = Paths.get(RESOURCES + folderToZip);
        zip = Paths.get(RESOURCES + folderToZip + ZIP_EXTENSION);
        ZipUtil.zip(zip, inputDir);
    }

    public void after() {
        try {
            Files.deleteIfExists(zip);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
