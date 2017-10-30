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
 */

package stroom.pipeline.shared;

import stroom.dispatch.shared.Action;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefs;
import stroom.util.shared.SharedSet;

import java.util.Set;

public class FetchDocRefsAction extends Action<SharedSet<DocRef>> {
    private static final long serialVersionUID = -1773546031158236156L;

    private Set<DocRef> docRefs;

    public FetchDocRefsAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchDocRefsAction(final Set<DocRef> docRefs) {
        this.docRefs = docRefs;
    }

    public Set<DocRef> getDocRefs() {
        return docRefs;
    }

    public void setDocRefs(final Set<DocRef> docRefs) {
        this.docRefs = docRefs;
    }

    @Override
    public String getTaskName() {
        return "FetchDocRefsAction";
    }
}
