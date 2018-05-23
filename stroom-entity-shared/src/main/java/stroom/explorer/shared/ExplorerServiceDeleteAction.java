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
import stroom.docref.DocRef;

import java.util.List;

public class ExplorerServiceDeleteAction extends Action<BulkActionResult> {
    private static final long serialVersionUID = 800905016214418723L;

    private List<DocRef> docRefs;

    public ExplorerServiceDeleteAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerServiceDeleteAction(final List<DocRef> docRefs) {
        this.docRefs = docRefs;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    @Override
    public String getTaskName() {
        return "Delete " + docRefs.size() + " documents";
    }
}