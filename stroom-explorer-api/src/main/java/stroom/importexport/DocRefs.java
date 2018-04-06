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

package stroom.importexport;

import io.swagger.annotations.ApiModel;
import stroom.query.api.v2.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Set;

@XmlType(name = "DocRefs")
@XmlRootElement(name = "docRefs")
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A set of doc refs")
public class DocRefs implements Serializable {
    private Set<DocRef> set;

    private DocRefs() {
    }

    public DocRefs(final Set<DocRef> set) {
        this.set = set;
    }

    public Set<DocRef> getSet() {
        return set;
    }
}
