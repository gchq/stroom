/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.docstore.api;


import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.util.shared.Document;

import java.util.Objects;

public interface DocumentActionHandler<D extends Document> {

    D readDocument(DocRef docRef);

    D writeDocument(D document);

    /**
     * @return The {@link DocRef} type that this handler supports.
     */
    String getType();

    /**
     * Retrieve the audit information for a particular doc ref
     *
     * @param docRef The docRef to return the information for
     * @return The Audit information about the given DocRef.
     */
    DocRefInfo info(DocRef docRef);

    static DocRefInfo getDocRefInfo(final Document document) {

        Objects.requireNonNull(document);

        return DocRefInfo
                .builder()
                .docRef(DocRef.builder()
                        .type(document.getType())
                        .uuid(document.getUuid())
                        .name(document.getName())
                        .build())
                .createTime(document.getCreateTimeMs())
                .createUser(document.getCreateUser())
                .updateTime(document.getUpdateTimeMs())
                .updateUser(document.getUpdateUser())
                .build();
    }
}
