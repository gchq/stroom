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

package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
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
     * Exports data using the latest version of the export format
     * (assuming Guice is set up correctly and you get the right
     * implementation).
     * @param rootNode         Path to root node of the export. If null then
     *                         performs the same as the other write() method.
     *                         Otherwise removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param docRefs          Set of the DocRefs to serialize.
     * @param typesToIgnore    Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null.
     * @param omitAuditFields  Do not export audit fields.
     * @return summary of the export.
     */
    ExportSummary write(final List<ExplorerNode> rootNode,
                        final Path dir,
                        final Set<DocRef> docRefs,
                        final Set<String> typesToIgnore,
                        final boolean omitAuditFields);

    /**
     * Exports data using the given version of the export format
     * (assuming Guice is set up correctly and you get the right
     * implementation).
     * @param rootNodePath     Path to root node of the export. If null then
     *                         performs the same as the other write() method.
     *                         Otherwise removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param docRefs          Set of the DocRefs to serialize.
     * @param typesToIgnore    Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null.
     * @param omitAuditFields  Do not export audit fields.
     * @param version          Version of the export format to use.
     * @return summary of the export.
     */
    ExportSummary write(List<ExplorerNode> rootNodePath,
                        Path dir,
                        Set<DocRef> docRefs,
                        Set<String> typesToIgnore,
                        boolean omitAuditFields,
                        ImportExportVersion version);
}
