/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.explorer.shared;

import stroom.util.shared.SharedObject;

public class DocumentType implements SharedObject {
    public static final String DOC_IMAGE_URL = "document/";

    private static final long serialVersionUID = -7826692935161793565L;

    private boolean system;
    private int priority;
    private String type;
    private String displayType;
    private String iconUrl;

    public DocumentType() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocumentType(final boolean system, final int priority, final String type, final String displayType, final String iconUrl) {
        this.system = system;
        this.priority = priority;
        this.type = type;
        this.displayType = displayType;
        this.iconUrl = iconUrl;
    }

    public boolean isSystem() {
        return system;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayType() {
        return displayType;
    }

    public String getType() {
        return type;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DocumentType that = (DocumentType) o;

        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return type;
    }
}
