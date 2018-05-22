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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import stroom.docref.SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LayoutConfig")
@XmlSeeAlso({SplitLayoutConfig.class, TabLayoutConfig.class})
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SplitLayoutConfig.class, name = "splitLayout"),
        @JsonSubTypes.Type(value = TabLayoutConfig.class, name = "tabLayout")
})
public abstract class LayoutConfig implements SharedObject {
    private static final long serialVersionUID = 8743223047838956165L;

    private transient SplitLayoutConfig parent;

    public abstract Size getPreferredSize();

    public abstract void setPreferredSize(Size preferredSize);

    public SplitLayoutConfig getParent() {
        return parent;
    }

    public void setParent(final SplitLayoutConfig parent) {
        this.parent = parent;
    }
}
