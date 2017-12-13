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

package stroom.stream;


import stroom.entity.shared.DocumentEntity;

import javax.persistence.Transient;

public class OldFolder extends DocumentEntity {
    public static final String ENTITY_TYPE = "Folder";
    private static final long serialVersionUID = -4208920620555926044L;

    private OldFolder folder;

    public static OldFolder create(final OldFolder parent, final String name) {
        final OldFolder folder = new OldFolder();
        folder.setFolder(parent);
        folder.setName(name);
        return folder;
    }

    public static final OldFolder createStub(final long pk) {
        final OldFolder folder = new OldFolder();
        folder.setStub(pk);
        return folder;
    }

    /**
     * The parent folder
     */
    public OldFolder getFolder() {
        return folder;
    }

    public void setFolder(final OldFolder folder) {
        this.folder = folder;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
