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

package stroom.util;

import org.junit.rules.ExternalResource;
import stroom.util.io.StreamUtil;
import stroom.util.zip.ZipUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipInputStream;

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
public class ZipResource extends ExternalResource {
    private static final String RESOURCES = "src/test/resources/";
    private static final String ZIP_EXTENSION = ".zip";

    private final String folderToZip;
    private File zip;

    public ZipResource(String folderToZip) {
        this.folderToZip = folderToZip;
    }

    public Path getPath() {
        return Paths.get(RESOURCES + folderToZip + ZIP_EXTENSION);
    }

    public InputStream getStream() throws FileNotFoundException {
        return new FileInputStream(getPath().toFile());
    }

    public byte[] getBytes() throws FileNotFoundException {
        return StreamUtil.streamToBuffer(getStream()).toByteArray();
    }

    public ZipInputStream getZipInputStream() throws FileNotFoundException {
        return new ZipInputStream(new ByteArrayInputStream(getBytes()));
    }

    @Override
    public void before() throws IOException {
        File inputDir = new File(RESOURCES + folderToZip);
        zip = new File(RESOURCES + folderToZip + ZIP_EXTENSION);
        ZipUtil.zip(zip, inputDir);
    }

    @Override
    public void after() {
        try {
            Files.deleteIfExists(zip.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
