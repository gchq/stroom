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

package stroom.explorer.shared;

import stroom.docref.SharedObject;

import java.util.List;

public class DocumentTypes implements SharedObject {
    private static final long serialVersionUID = -8432367046243288634L;

    public static final String[] FOLDER_TYPES = new String[]{ExplorerConstants.SYSTEM, ExplorerConstants.FOLDER};

    private List<DocumentType> nonSystemTypes;
    private List<DocumentType> visibleTypes;

    public DocumentTypes() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocumentTypes(final List<DocumentType> nonSystemTypes, final List<DocumentType> visibleTypes) {
        this.nonSystemTypes = nonSystemTypes;
        this.visibleTypes = visibleTypes;
    }

    public List<DocumentType> getNonSystemTypes() {
        return nonSystemTypes;
    }

    public List<DocumentType> getVisibleTypes() {
        return visibleTypes;
    }

    public static boolean isFolder(final String type) {
        return ExplorerConstants.FOLDER.equals(type) || ExplorerConstants.SYSTEM.equals(type);
    }

    public static boolean isSystem(final String type) {
        return ExplorerConstants.SYSTEM.equals(type);
    }
}
