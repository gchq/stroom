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

package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import stroom.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LayoutConfig")
@XmlSeeAlso({_V07_00_00_SplitLayoutConfig.class, _V07_00_00_TabLayoutConfig.class})
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = _V07_00_00_SplitLayoutConfig.class, name = "splitLayout"),
        @JsonSubTypes.Type(value = _V07_00_00_TabLayoutConfig.class, name = "tabLayout")
})
public abstract class _V07_00_00_LayoutConfig implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = 8743223047838956165L;

    private transient _V07_00_00_SplitLayoutConfig parent;

    public abstract _V07_00_00_Size getPreferredSize();

    public abstract void setPreferredSize(_V07_00_00_Size preferredSize);

    public _V07_00_00_SplitLayoutConfig getParent() {
        return parent;
    }

    public void setParent(final _V07_00_00_SplitLayoutConfig parent) {
        this.parent = parent;
    }
}
