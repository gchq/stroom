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

package stroom.core.db.migration._V07_00_00.streamstore.shared;


import stroom.entity.shared.DocumentEntity;

@Deprecated
public class _V07_00_00_Folder extends DocumentEntity {
    public static final String ENTITY_TYPE = "Folder";
    private static final long serialVersionUID = -4208920620555926044L;

    private _V07_00_00_Folder folder;

    public static _V07_00_00_Folder create(final _V07_00_00_Folder parent, final String name) {
        final _V07_00_00_Folder folder = new _V07_00_00_Folder();
        folder.setFolder(parent);
        folder.setName(name);
        return folder;
    }

    public static final _V07_00_00_Folder createStub(final long pk) {
        final _V07_00_00_Folder folder = new _V07_00_00_Folder();
        folder.setStub(pk);
        return folder;
    }

    /**
     * The parent folder
     */
    public _V07_00_00_Folder getFolder() {
        return folder;
    }

    public void setFolder(final _V07_00_00_Folder folder) {
        this.folder = folder;
    }

    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
