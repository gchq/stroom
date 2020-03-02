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

package stroom.core.db.migration._V07_00_00.doc.pipeline;

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Elements", propOrder = {"add", "remove"})
public class _V07_00_00_PipelineElements implements _V07_00_00_SharedObject {
    private static final long serialVersionUID = -2207621085023679751L;

    @XmlElementWrapper(name = "add")
    @XmlElement(name = "element")
    private List<_V07_00_00_PipelineElement> add = new ArrayList<>();

    @XmlElementWrapper(name = "remove")
    @XmlElement(name = "element")
    private List<_V07_00_00_PipelineElement> remove = new ArrayList<>();

    public List<_V07_00_00_PipelineElement> getAdd() {
        return add;
    }

    public List<_V07_00_00_PipelineElement> getRemove() {
        return remove;
    }
}
