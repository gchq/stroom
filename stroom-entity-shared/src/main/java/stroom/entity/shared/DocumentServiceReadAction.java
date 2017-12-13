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

package stroom.entity.shared;

import stroom.query.api.v2.DocRef;
import stroom.util.shared.SharedObject;

public class DocumentServiceReadAction<D extends SharedObject> extends AbstractEntityAction<D> {
    private static final long serialVersionUID = 800905016214418723L;

    public DocumentServiceReadAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocumentServiceReadAction(final DocRef docRef) {
        super(docRef, "Read: " + docRef);
    }
}
