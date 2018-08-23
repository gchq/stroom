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

package stroom.db.migration.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlSeeAlso({QueryComponentSettings.class, TableComponentSettings.class, VisComponentSettings.class, TextComponentSettings.class})
@JsonTypeInfo(
        use = Id.NAME,
        include = As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QueryComponentSettings.class, name = "query"),
        @JsonSubTypes.Type(value = TableComponentSettings.class, name = "table"),
        @JsonSubTypes.Type(value = VisComponentSettings.class, name = "vis"),
        @JsonSubTypes.Type(value = TextComponentSettings.class, name = "text")
})
public abstract class ComponentSettings implements Serializable {
    private static final long serialVersionUID = 2110282486749818888L;

    public ComponentSettings() {
        // Default constructor necessary for GWT serialisation.
    }
}
