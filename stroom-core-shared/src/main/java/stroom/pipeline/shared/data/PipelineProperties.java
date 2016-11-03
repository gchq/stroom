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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;

import stroom.util.shared.SharedObject;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Properties", propOrder = { "add", "remove" })
public class PipelineProperties implements SharedObject {
    private static final long serialVersionUID = -7892582092797084330L;

    @XmlElementWrapper(name = "add", required = false)
    @XmlElement(name = "property", required = false)
    private List<PipelineProperty> add = new ArrayList<>();

    @XmlElementWrapper(name = "remove", required = false)
    @XmlElement(name = "property", required = false)
    private List<PipelineProperty> remove = new ArrayList<>();

    public List<PipelineProperty> getAdd() {
        return add;
    }

    public List<PipelineProperty> getRemove() {
        return remove;
    }
}
