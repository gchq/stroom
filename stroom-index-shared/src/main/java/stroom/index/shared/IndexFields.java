/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.index.shared;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@XmlRootElement(name = "fields")
public class IndexFields implements Serializable {
    private static final long serialVersionUID = 4457718308915039068L;

    private List<IndexField> indexFields;

    public IndexFields() {
        this.indexFields = new ArrayList<>();
    }

    public IndexFields(final List<IndexField> indexFields) {
        this.indexFields = indexFields;
    }

    public static IndexFields createStreamIndexFields() {
        final List<IndexField> indexFields = new ArrayList<>();
        // Always add standard id fields for now.
        indexFields.add(IndexField.createIdField(IndexConstants.STREAM_ID));
        indexFields.add(IndexField.createIdField(IndexConstants.EVENT_ID));
        return new IndexFields(indexFields);
    }

    public void addIndexField(final IndexField indexField) {
        if (indexFields == null) {
            indexFields = new ArrayList<>();
        }
        indexFields.add(indexField);
    }

    @XmlElements({@XmlElement(name = "field", type = IndexField.class)})
    public List<IndexField> getIndexFields() {
        return indexFields;
    }

    public void setIndexFields(final List<IndexField> indexFields) {
        this.indexFields = indexFields;
    }

    public void add(final IndexField indexField) {
        if (indexFields == null) {
            indexFields = new ArrayList<>();
        }
        indexFields.add(indexField);
    }

    public void remove(final IndexField indexField) {
        if (indexFields != null) {
            indexFields.remove(indexField);
        }
    }

    public boolean contains(final IndexField indexField) {
        if (indexFields != null) {
            return indexFields.contains(indexField);
        }
        return false;
    }

    public Set<String> getFieldNames() {
        final Set<String> set = new HashSet<>();
        if (indexFields != null) {
            for (final IndexField field : indexFields) {
                set.add(field.getFieldName());
            }
        }
        return set;
    }
}
