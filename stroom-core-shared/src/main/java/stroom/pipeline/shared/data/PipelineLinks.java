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



import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Links", propOrder = {"add", "remove"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"add", "remove"})
public class PipelineLinks {
    @XmlElementWrapper(name = "add", required = false)
    @XmlElement(name = "link", required = false)
    private List<PipelineLink> add = new ArrayList<>();

    @XmlElementWrapper(name = "remove", required = false)
    @XmlElement(name = "link", required = false)
    private List<PipelineLink> remove = new ArrayList<>();

    public List<PipelineLink> getAdd() {
        return add;
    }

    public void setAdd(final List<PipelineLink> add) {
        this.add = add;
    }

    public List<PipelineLink> getRemove() {
        return remove;
    }

    public void setRemove(final List<PipelineLink> remove) {
        this.remove = remove;
    }
}
