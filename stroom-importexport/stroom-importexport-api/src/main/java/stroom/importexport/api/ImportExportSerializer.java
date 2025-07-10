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

package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.ExportContentRequest;
import stroom.importexport.shared.ExportSummary;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ImportExportSerializer {

    /**
     * Read all the serialized DocRef items from the supplied path
     *
     * @param dir             directory containing serialized DocRef items, e.g. files created by
     *                        ImportExportSerializer.write()
     * @param importStateList
     * @param importSettings
     * @return The set of all DocRef roots, typically this is the Explorer root DocRef plus any DocRefs
     * not held in the Explorer tree.
     */
    Set<DocRef> read(Path dir,
                     List<ImportState> importStateList,
                     ImportSettings importSettings);

    /**
     * Walk the supplied tree of DocRefs and export all to the given path
     *
     * @param dir             Where to serialize the DocRef items to.
     * @param request         Set of the DocRefs and root folder DocRefs (as per that returned by
     *                        ImportExportSerializer.read()
     * @param omitAuditFields do not export audit fields (e.g. last update time / last update user)
     * @param exportMode      Whether to perform the import or do a dry-run.
     * @return summary of the export.
     */
    ExportSummary write(final Path dir,
                        final ExportContentRequest request,
                        final boolean omitAuditFields,
                        final ExportMode exportMode);

    /**
     * Variant of the write() method to be used when exporting to Git.
     *
     * @param rootNodePath     Path to root node of the export. If null then
     *                         performs the same as the other write() method.
     *                         Otherwise removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param request          Set of the DocRefs to serialize.
     * @param docTypesToIgnore Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null.
     * @param omitAuditFields  Do not export audit fields.
     * @param exportMode       Whether to perform the import or do a dry-run.
     * @return summary of the export.
     */
    ExportSummary write(final List<ExplorerNode> rootNodePath,
                        final Path dir,
                        final ExportContentRequest request,
                        final Set<String> docTypesToIgnore,
                        final boolean omitAuditFields,
                        final ExportMode exportMode);
}
