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

package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;

@JsonPropertyOrder({"docRef", "changeSet", "cascade"})
@JsonInclude(Include.NON_DEFAULT)
public class ChangeDocumentPermissionsRequest {
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final ChangeSet<UserPermission> changeSet;
    @JsonProperty
    private final Cascade cascade;

    @JsonCreator
    public ChangeDocumentPermissionsRequest(@JsonProperty("docRef") final DocRef docRef,
                                            @JsonProperty("changeSet") final ChangeSet<UserPermission> changeSet,
                                            @JsonProperty("cascade") final Cascade cascade) {
        this.docRef = docRef;
        this.changeSet = changeSet;
        this.cascade = cascade;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public ChangeSet<UserPermission> getChangeSet() {
        return changeSet;
    }

    public Cascade getCascade() {
        return cascade;
    }

    public enum Cascade implements HasDisplayValue {
        NO("No"), CHANGES_ONLY("Changes only"), ALL("All");

        private final String displayValue;

        Cascade(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
