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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LayoutConfig")
@XmlSeeAlso({SplitLayoutConfig.class, TabLayoutConfig.class})
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SplitLayoutConfig.class, name = "splitLayout"),
        @JsonSubTypes.Type(value = TabLayoutConfig.class, name = "tabLayout")
})
public abstract class LayoutConfig {
    @JsonIgnore
    private transient SplitLayoutConfig parent;

    public abstract Size getPreferredSize();

    @JsonIgnore
    public SplitLayoutConfig getParent() {
        return parent;
    }

    @JsonIgnore
    public void setParent(final SplitLayoutConfig parent) {
        this.parent = parent;
    }
}
