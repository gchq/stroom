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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

@JsonInclude(Include.NON_NULL)
public class ExplorerServiceRenameRequest {
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String docName;

    @JsonCreator
    public ExplorerServiceRenameRequest(@JsonProperty("docRef") final DocRef docRef,
                                        @JsonProperty("docName") final String docName) {
        this.docRef = docRef;
        this.docName = docName;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getDocName() {
        return docName;
    }
}