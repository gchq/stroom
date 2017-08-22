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

package stroom.entity.shared;

import stroom.query.api.v2.DocRef;
import stroom.util.shared.SharedObject;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

@XmlRootElement(name = "docs")
public class DocRefs implements Iterable<DocRef>, SharedObject {
    private static final long serialVersionUID = 8637215303311013483L;
    private Set<DocRef> set = new TreeSet<>();

    public DocRefs() {
        // Default constructor necessary for GWT serialisation.
    }

    public boolean add(DocRef docRef) {
        return set.add(docRef);
    }

    public boolean remove(DocRef docRef) {
        return set.remove(docRef);
    }

    @Override
    public Iterator<DocRef> iterator() {
        return set.iterator();
    }

    /**
     * HERE FOR XML JAXB serialisation ..... DO NOT REMOVE
     */
    public Collection<DocRef> getDoc() {
        return set;
    }

    /**
     * HERE FOR XML JAXB serialisation ..... DO NOT REMOVE
     */
    public void setDoc(final Collection<DocRef> newSet) {
        set = new TreeSet<>(newSet);
    }
}
