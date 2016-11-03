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

package stroom.pipeline.shared.data;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HasType;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

public class PipelinePropertyType implements Comparable<PipelinePropertyType>, HasType, SharedObject {
    private static final long serialVersionUID = 2290622144151007980L;

    private PipelineElementType elementType;
    private String name;
    private String type;
    private String description;
    private String defaultValue;
    private boolean pipelineReference;
    private boolean dataEntity;

    public PipelinePropertyType() {
        // Default constructor necessary for GWT serialisation.
    }

    public PipelinePropertyType(final PipelineElementType elementType, final String name, final String type) {
        this(elementType, name, type, null, null, false, false);
    }

    public PipelinePropertyType(final PipelineElementType elementType, final String name, final String type,
            final String description, final String defaultValue, final boolean pipelineReference,
            final boolean dataEntity) {
        this.elementType = elementType;
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
        this.pipelineReference = pipelineReference;
        this.dataEntity = dataEntity;
    }

    public PipelineElementType getElementType() {
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

    public boolean isDataEntity() {
        return dataEntity;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PipelinePropertyType)) {
            return false;
        }

        final PipelinePropertyType pipelinePropertyType = (PipelinePropertyType) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(elementType, pipelinePropertyType.elementType);
        builder.append(name, pipelinePropertyType.name);
        builder.append(type, pipelinePropertyType.type);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(elementType);
        builder.append(name);
        builder.append(type);
        return builder.toHashCode();
    }

    @Override
    public int compareTo(final PipelinePropertyType o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
