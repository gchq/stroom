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

import stroom.docref.DocRef;
import stroom.docref.SharedObject;

import java.util.List;

public class BulkActionResult implements SharedObject {
    private List<DocRef> docRefs;
    private String message;

    public BulkActionResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public BulkActionResult(final List<DocRef> docRefs, final String message) {
        this.docRefs = docRefs;
        this.message = message;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    public String getMessage() {
        return message;
    }
}
