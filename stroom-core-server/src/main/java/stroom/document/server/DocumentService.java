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

import stroom.entity.shared.PermissionInheritance;
import stroom.query.api.v1.DocRef;

interface DocumentService {
    Object read(DocRef docRef);

    Object write(DocRef docRef, Object document);

    Object fork(DocRef docRef, Object document, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance);

    void delete(DocRef docRef);
}
