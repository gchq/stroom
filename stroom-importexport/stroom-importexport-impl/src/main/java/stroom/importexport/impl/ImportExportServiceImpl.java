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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.Message;
import stroom.util.zip.ZipUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Service to export standing data in and out from Stroom. It uses a ZIP format to
 * hold serialised Stroom content.
 */
public class ImportExportServiceImpl implements ImportExportService {

    private final ImportExportSerializer importExportSerializer;
    private final TempDirProvider tempDirProvider;

    @Inject
    public ImportExportServiceImpl(final ImportExportSerializer importExportSerializer,
                                   final TempDirProvider tempDirProvider) {
        this.importExportSerializer = importExportSerializer;
        this.tempDirProvider = tempDirProvider;
    }

    @Override
    public List<ImportState> importConfig(final Path data,
                                          final ImportSettings importSettings,
                                          final List<ImportState> confirmList) {
        doImport(data, confirmList, importSettings);
        confirmList.sort(Comparator.comparing(ImportState::getSourcePath));
        return confirmList;
    }

    private void doImport(final Path zipFile,
                          final List<ImportState> confirmList,
                          final ImportSettings importSettings) {
        final Path explodeDir = workingZipDir(zipFile);
        try {
            Files.createDirectories(explodeDir);

            // Unzip the zip file.
            ZipUtil.unzip(zipFile, explodeDir);

            importExportSerializer.read(explodeDir, confirmList, importSettings);
        } catch (final IOException | RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            FileUtil.deleteDir(explodeDir);
        }
    }

    /**
     * Export the selected folder data.
     */
    @Override
    public void exportConfig(final Set<DocRef> docRefs, final Path zipFile,
                             final List<Message> messageList) {
        final Path explodeDir = workingZipDir(zipFile);
        try {
            Files.createDirectories(explodeDir);

            // Serialize the config in a human readable tree structure.
            importExportSerializer.write(explodeDir, docRefs, true, messageList);

            // Now zip the dir.
            ZipUtil.zip(zipFile, explodeDir);

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            FileUtil.deleteDir(explodeDir);
        }
    }

    private Path workingZipDir(final Path zipFile) {
        final String name = zipFile.getFileName().toString();
        return tempDirProvider.get().resolve(name.substring(0, name.length() - ".zip".length()));
    }
}
