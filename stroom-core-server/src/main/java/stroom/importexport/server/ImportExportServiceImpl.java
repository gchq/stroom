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

package stroom.importexport.server;

import java.io.File;
import java.util.List;

import javax.annotation.Resource;

import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;

import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.entity.shared.FindFolderCriteria;
import stroom.importexport.server.ImportExportSerializer.ImportMode;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.shared.SharedList;
import stroom.util.zip.ZipUtil;

/**
 * Service to export standing data in and out from Stroom. It uses a ZIP format to
 * hold a HSQLDB database.
 */
@Component
public class ImportExportServiceImpl implements ImportExportService {
    protected static final StroomLogger LOGGER = StroomLogger.getLogger(ImportExportServiceImpl.class);

    @Resource
    private ImportExportSerializer importExportSerializer;

    /**
     * Export the selected folder data.
     */
    @Override
    public void exportConfig(final FindFolderCriteria criteria, final File zipFile, final boolean ignoreErrors,
            final List<String> messageList) {
        final File explodeDir = ZipUtil.workingZipDir(zipFile);
        explodeDir.mkdirs();
        try {
            // Serialize the config in a human readable tree structure.
            importExportSerializer.write(explodeDir, criteria, false, ignoreErrors, messageList);

            // Now zip the dir.
            ZipUtil.zip(zipFile, explodeDir);

        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        } finally {
            FileSystemUtil.deleteDirectory(explodeDir);
        }
    }

    @Override
    public SharedList<EntityActionConfirmation> createImportConfirmationList(final File data) {
        final SharedList<EntityActionConfirmation> confirmList = new SharedList<>();
        doImport(data, confirmList, ImportMode.CREATE_CONFIRMATION);
        return confirmList;
    }

    /**
     * Import API.
     */
    @Override
    public void performImportWithConfirmation(final File data, final List<EntityActionConfirmation> confirmList) {
        doImport(data, confirmList, ImportMode.ACTION_CONFIRMATION);
    }

    @Override
    public void performImportWithoutConfirmation(final File data) {
        doImport(data, null, ImportMode.IGNORE_CONFIRMATION);
    }

    private void doImport(final File zipFile, final List<EntityActionConfirmation> confirmList,
            final ImportMode importMode) {
        final File explodeDir = ZipUtil.workingZipDir(zipFile);
        explodeDir.mkdirs();

        try {
            // Unzip the zip file.
            ZipUtil.unzip(zipFile, explodeDir);

            importExportSerializer.read(explodeDir, confirmList, importMode);
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        } finally {
            FileSystemUtil.deleteDirectory(explodeDir);
        }
    }
}
