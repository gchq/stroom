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

package stroom.entity.shared;

/**
 * Interface that represents a filter by folders.
 */
public class FindFolderCriteria extends FindDocumentEntityCriteria {
    public static final OrderBy ORDER_BY_DESCRIPTION = new OrderBy("Description", "description", true);
    public static final OrderBy ORDER_BY_STATUS = new OrderBy("Status", "status");
    private static final long serialVersionUID = 2559262939309086599L;
    /**
     * If the FolderIdSet is related our id's not our parents
     */
    private boolean self = false;

    public FindFolderCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public boolean isSelf() {
        return self;
    }

    public void setSelf(final boolean self) {
        this.self = self;
    }

    public void copyFrom(final FindFolderCriteria other) {
        this.self = other.self;
        super.copyFrom(other);
    }
}
