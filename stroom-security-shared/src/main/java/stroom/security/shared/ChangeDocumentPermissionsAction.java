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

import stroom.entity.shared.Action;
import stroom.query.api.v1.DocRef;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.VoidResult;

public class ChangeDocumentPermissionsAction extends Action<VoidResult> {
    private static final long serialVersionUID = -6740095230475597845L;
    private DocRef docRef;
    private ChangeSet<UserPermission> changeSet;
    private Cascade cascade;
    public ChangeDocumentPermissionsAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ChangeDocumentPermissionsAction(final DocRef docRef, final ChangeSet<UserPermission> changeSet, final Cascade cascade) {
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

    public void setCascade(Cascade cascade) {
        this.cascade = cascade;
    }

    @Override
    public String getTaskName() {
        return "Change Document Permissions";
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
