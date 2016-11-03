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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

public abstract class FindDocumentEntityCriteria extends FindNamedEntityCriteria
        implements HasFolderIdSet, Copyable<FindDocumentEntityCriteria> {
    private static final long serialVersionUID = -970306839701196839L;

    public static final OrderBy ORDER_BY_FOLDER = new OrderBy("Folder", "folder.name", true);

    /**
     * Sub Filter - used for restricting items to their parent folder
     */
    private FolderIdSet folderIdSet = new FolderIdSet();

    private String requiredPermission;

    public FindDocumentEntityCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindDocumentEntityCriteria(final String name) {
        super(name);
    }

    @Override
    public FolderIdSet getFolderIdSet() {
        return folderIdSet;
    }

    @Override
    public FolderIdSet obtainFolderIdSet() {
        return folderIdSet;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(final String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(folderIdSet);
        builder.append(requiredPermission);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof FindDocumentEntityCriteria)) {
            return false;
        }

        final FindDocumentEntityCriteria criteria = (FindDocumentEntityCriteria) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(folderIdSet, criteria.folderIdSet);
        builder.append(requiredPermission, criteria.requiredPermission);
        return builder.isEquals();
    }

    @Override
    public void copyFrom(final FindDocumentEntityCriteria other) {
        this.folderIdSet.copyFrom(other.folderIdSet);
        this.requiredPermission = other.requiredPermission;
        super.copyFrom(other);
    }
}
