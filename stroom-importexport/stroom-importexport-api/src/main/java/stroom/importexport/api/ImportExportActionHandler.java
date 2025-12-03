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
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.HasDependencies;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportExportActionHandler extends HasDependencies {

    /**
     * @param docRef
     * @param dataMap
     * @param importState
     * @param importSettings
     * @return a tuple containing the imported DocRef and a String location where it is imported to
     */
    DocRef importDocument(DocRef docRef,
                          Map<String, byte[]> dataMap,
                          ImportState importState,
                          ImportSettings importSettings);

    Map<String, byte[]> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);

    Set<DocRef> listDocuments();

    String getType();

    Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef);
}
