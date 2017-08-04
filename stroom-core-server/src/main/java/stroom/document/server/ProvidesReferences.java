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
 *
 */

package stroom.document.server;

import stroom.query.api.v1.DocRef;

import java.util.Map;
import java.util.Set;

/**
 * This interface is designed to help users maintain referential integrity in their system by providing a map keyed by document with values that are sets of documents that are referenced. The system does not enforce strict referential integrity due to the lack of flexibility this would cause, e.g. difficulties in deleting anything, so being able to determine references to provide appropriate warning on deletion or any subsequent referential integrity check is very useful.
 */
public interface ProvidesReferences {
    /**
     * For helping users maintain referential integrity in their system, get a map of documents and the set of documents that they reference.
     *
     * @return A map of documents and the set of documents that they reference.
     */
    Map<DocRef, Set<DocRef>> getReferences();
}
