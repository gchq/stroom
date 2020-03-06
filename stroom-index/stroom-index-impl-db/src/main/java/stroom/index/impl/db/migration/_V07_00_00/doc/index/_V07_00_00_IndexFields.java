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
 */

package stroom.index.impl.db.migration._V07_00_00.doc.index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.index.shared.IndexConstants;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@XmlRootElement(name = "fields")
public class _V07_00_00_IndexFields implements Serializable {
    private static final long serialVersionUID = 4457718308915039068L;

    private List<_V07_00_00_IndexField> indexFields;

    public _V07_00_00_IndexFields() {
        this.indexFields = new ArrayList<>();
    }

    public _V07_00_00_IndexFields(final List<_V07_00_00_IndexField> indexFields) {
        this.indexFields = indexFields;
    }

    public static List<_V07_00_00_IndexField> createStreamIndexFields() {
        final List<_V07_00_00_IndexField> indexFields = new ArrayList<>();
        // Always add standard id fields for now.
        indexFields.add(_V07_00_00_IndexField.createIdField(IndexConstants.STREAM_ID));
        indexFields.add(_V07_00_00_IndexField.createIdField(IndexConstants.EVENT_ID));
        return indexFields;
    }

    @XmlElements({@XmlElement(name = "field", type = _V07_00_00_IndexField.class)})
    public List<_V07_00_00_IndexField> getIndexFields() {
        return indexFields;
    }

    @JsonIgnore
    public void add(final _V07_00_00_IndexField indexField) {
        indexFields.add(indexField);
    }

    @JsonIgnore
    public void remove(final _V07_00_00_IndexField indexField) {
        indexFields.remove(indexField);
    }

    @JsonIgnore
    public boolean contains(final _V07_00_00_IndexField indexField) {
        return indexFields != null && indexFields.contains(indexField);
    }

    @JsonIgnore
    public Set<String> getFieldNames() {
        final Set<String> set = new HashSet<>();
        for (final _V07_00_00_IndexField field : indexFields) {
            set.add(field.getFieldName());
        }
        return set;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final _V07_00_00_IndexFields that = (_V07_00_00_IndexFields) o;
        return Objects.equals(indexFields, that.indexFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexFields);
    }
}
