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

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class DocumentEntity extends NamedEntity implements Document, HasFolder {
    public static final String UUID = SQLNameConstants.UUID;

    private static final long serialVersionUID = -6752797140242673318L;

    private String uuid;
    private Folder folder;

    @Override
    @Column(name = UUID, unique = true, nullable = false)
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    @Override
    public void clearPersistence() {
        super.clearPersistence();
        uuid = null;
    }

    /**
     * The parent folder
     */
    @Override
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Folder.FOREIGN_KEY)
    public Folder getFolder() {
        return folder;
    }

    @Override
    public void setFolder(final Folder folder) {
        this.folder = folder;
    }

    protected void copyFrom(final DocumentEntity t) {
        this.folder = t.folder;
        super.copyFrom(t);
    }
}
