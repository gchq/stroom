/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.explorer.shared;

import stroom.entity.shared.Action;
import stroom.entity.shared.SharedDocRef;
import stroom.docref.DocRef;

public class ExplorerServiceRenameAction extends Action<SharedDocRef> {
    private static final long serialVersionUID = 800905016214418723L;

    private DocRef docRef;
    private String docName;

    public ExplorerServiceRenameAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerServiceRenameAction(final DocRef docRef, final String docName) {
        this.docRef = docRef;
        this.docName = docName;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getDocName() {
        return docName;
    }

    @Override
    public String getTaskName() {
        return "Rename '" + docRef + "' to '" + docName + "'";
    }
}