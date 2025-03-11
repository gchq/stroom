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


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Links", propOrder = {"add", "remove"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"add", "remove"})
public class PipelineLinks {

    @XmlElementWrapper(name = "add")
    @XmlElement(name = "link")
    @JsonProperty
    private final List<PipelineLink> add;

    @XmlElementWrapper(name = "remove")
    @XmlElement(name = "link")
    @JsonProperty
    private final List<PipelineLink> remove;

    public PipelineLinks() {
        add = new ArrayList<>();
        remove = new ArrayList<>();
    }

    @JsonCreator
    public PipelineLinks(@JsonProperty("add") final List<PipelineLink> add,
                         @JsonProperty("remove") final List<PipelineLink> remove) {
        this.add = add;
        this.remove = remove;
    }

    public List<PipelineLink> getAdd() {
        return add;
    }

    public List<PipelineLink> getRemove() {
        return remove;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineLinks that = (PipelineLinks) o;
        return Objects.equals(add, that.add) &&
               Objects.equals(remove, that.remove);
    }

    @Override
    public int hashCode() {
        return Objects.hash(add, remove);
    }
}
