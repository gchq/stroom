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

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * <p>
 * Used to hold system wide groups in the application.
 * </p>
 */
@Entity
@Table(name = "FOLDER", uniqueConstraints = @UniqueConstraint(columnNames = {"FK_FOLDER_ID", "NAME"}))
public class Folder extends DocumentEntity implements Copyable<Folder> {
    public static final String TABLE_NAME = SQLNameConstants.FOLDER;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "Folder";

    private static final long serialVersionUID = -4208920620555926044L;

    public static Folder create(final Folder parent, final String name) {
        final Folder folder = new Folder();
        folder.setFolder(parent);
        folder.setName(name);
        return folder;
    }

    public static final Folder createStub(final long pk) {
        final Folder folder = new Folder();
        folder.setStub(pk);
        return folder;
    }

    @Override
    public void copyFrom(final Folder other) {
        super.copyFrom(other);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
