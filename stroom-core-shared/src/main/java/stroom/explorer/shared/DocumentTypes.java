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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import stroom.util.shared.SharedObject;

public class DocumentTypes implements SharedObject {
    private static final long serialVersionUID = -8432367046243288634L;

    private List<DocumentType> allTypes;
    private List<DocumentType> visibleTypes;
    private transient Map<String, DocumentType> map;

    public DocumentTypes() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocumentTypes(final List<DocumentType> allTypes, final List<DocumentType> visibleTypes) {
        this.allTypes = allTypes;
        this.visibleTypes = visibleTypes;
    }

    public List<DocumentType> getAllTypes() {
        return allTypes;
    }

    public List<DocumentType> getVisibleTypes() {
        return visibleTypes;
    }

    public DocumentType getByName(final String name) {
        if (map == null) {
            map = new HashMap<>();
            if (allTypes != null) {
                allTypes.forEach(type -> map.put(type.getType(), type));
            }
        }
        return map.get(name);
    }
}
