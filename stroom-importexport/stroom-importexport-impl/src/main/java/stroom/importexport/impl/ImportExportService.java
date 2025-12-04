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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * API to get data in and out.
 */
public interface ImportExportService {

    List<ImportState> importConfig(Path data,
                                   ImportSettings importSettings,
                                   List<ImportState> confirmList);

    /**
     * Export a Stroom repository
     * <p>
     * Also in the zip file output content that can be exploded and stored in
     * source control. Used for tracking changes with XSLT and feeds.
     * @return
     */
    ExportSummary exportConfig(Set<DocRef> docRefs,
                               Path data);
}
