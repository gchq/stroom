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

package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TableResult.class, name = "table"),
        @JsonSubTypes.Type(value = FlatResult.class, name = "vis")
})
@XmlType(name = "Result", propOrder = "componentId")
@XmlSeeAlso({TableResult.class, FlatResult.class})
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class Result implements Serializable {
    private static final long serialVersionUID = -7455554742243923562L;

    @XmlElement
    private String componentId;

    Result() {
    }

    public Result(final String componentId) {
        this.componentId = componentId;
    }

    public String getComponentId() {
        return componentId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Result)) return false;

        final Result that = (Result) o;

        return componentId != null ? componentId.equals(that.componentId) : that.componentId == null;
    }

    @Override
    public int hashCode() {
        return componentId != null ? componentId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ComponentResult{" +
                "componentId='" + componentId + '\'' +
                '}';
    }
}