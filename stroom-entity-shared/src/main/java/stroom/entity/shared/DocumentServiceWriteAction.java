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

import stroom.query.api.v1.DocRef;
import stroom.util.shared.SharedObject;

public class DocumentServiceWriteAction<D extends SharedObject> extends AbstractEntityAction<D> {
    private static final long serialVersionUID = 800905016214418723L;

    private D document;

    public DocumentServiceWriteAction() {
        // Default constructor necessary for GWT serialisation.
    }

    @SuppressWarnings("unchecked")
    public DocumentServiceWriteAction(final Entity entity) {
        this(DocRefUtil.create(entity), (D) entity);
    }

    public DocumentServiceWriteAction(final DocRef docRef, final D document) {
        super(docRef, "Write: " + docRef);
        this.document = document;
    }

    public SharedObject getDocument() {
        return document;
    }
}
