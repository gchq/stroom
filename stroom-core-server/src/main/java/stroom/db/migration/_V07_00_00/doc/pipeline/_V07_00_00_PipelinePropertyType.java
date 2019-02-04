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

package stroom.db.migration._V07_00_00.doc.pipeline;

import stroom.db.migration._V07_00_00.docref._V07_00_00_SharedObject;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_HasType;
import stroom.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;

public class _V07_00_00_PipelinePropertyType
        implements Comparable<_V07_00_00_PipelinePropertyType>, _V07_00_00_HasType, _V07_00_00_SharedObject {
    private static final long serialVersionUID = 2290622144151007980L;

    private _V07_00_00_PipelineElementType elementType;
    private String name;
    private String type;
    private String description;
    private String defaultValue;
    private boolean pipelineReference;
    private String[] docRefTypes;

    public _V07_00_00_PipelinePropertyType() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_PipelineElementType getElementType() {
        return elementType;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isPipelineReference() {
        return pipelineReference;
    }

    public String[] getDocRefTypes() {
        return docRefTypes;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof _V07_00_00_PipelinePropertyType)) {
            return false;
        }

        final _V07_00_00_PipelinePropertyType pipelinePropertyType = (_V07_00_00_PipelinePropertyType) o;
        final _V07_00_00_EqualsBuilder builder = new _V07_00_00_EqualsBuilder();
        builder.append(elementType, pipelinePropertyType.elementType);
        builder.append(name, pipelinePropertyType.name);
        builder.append(type, pipelinePropertyType.type);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final _V07_00_00_HashCodeBuilder builder = new _V07_00_00_HashCodeBuilder();
        builder.append(elementType);
        builder.append(name);
        builder.append(type);
        return builder.toHashCode();
    }

    @Override
    public int compareTo(final _V07_00_00_PipelinePropertyType o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    public static class Builder {
        private final _V07_00_00_PipelinePropertyType instance;

        public Builder() {
            this.instance = new _V07_00_00_PipelinePropertyType();
        }

        public Builder elementType(final _V07_00_00_PipelineElementType value) {
            this.instance.elementType = value;
            return this;
        }

        public Builder name(final String value) {
            this.instance.name = value;
            return this;
        }

        public Builder type(final String value) {
            this.instance.type = value;
            return this;
        }

        public Builder description(final String value) {
            this.instance.description = value;
            return this;
        }

        public Builder defaultValue(final String value) {
            this.instance.defaultValue = value;
            return this;
        }

        public Builder pipelineReference(final boolean value) {
            this.instance.pipelineReference = value;
            return this;
        }

        public Builder docRefTypes(final String[] value) {
            this.instance.docRefTypes = value;
            return this;
        }

        public _V07_00_00_PipelinePropertyType build() {
            return instance;
        }
    }
}
