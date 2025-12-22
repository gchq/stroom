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

package stroom.docrefinfo.api;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;

import java.util.List;
import java.util.Set;

public interface DocRefDecorator {

    /**
     * Decorate the passed {@link DocRef}s with their names if the names are not present.
     * Null {@link DocRef}s are ignored and not returned. The passed list is not modified.
     *
     * @param docRefs A list of {@link DocRef} with at least their UUID and type set.
     * @return A list of fully populated {@link DocRef}s.
     */
    List<DocRef> decorate(final List<DocRef> docRefs);

    /**
     * Decorate the passed {@link DocRef} with its name if the name is not present.
     *
     * @param docRef A {@link DocRef} with at least the UUID and type set.
     * @return A fully populated {@link DocRef}.
     */
    default DocRef decorate(final DocRef docRef) {
        return decorate(docRef, false);
    }

    /**
     * Decorate the passed {@link DocRef} with its name if the name is not present.
     *
     * @param docRef A {@link DocRef} with at least the UUID and type set.
     * @param force  Decorate the name even if a name is present.
     *               This is to allow for docRefs with a name that is out of date with
     *               the document it represents, e.g. after a rename.
     * @return A fully populated {@link DocRef}.
     */
    DocRef decorate(final DocRef docRef, final boolean force);

    /**
     * Decorate the passed {@link DocRef} with its name if the name is not present.
     *
     * @param docRef              A {@link DocRef} with at least the UUID and type set.
     * @param force               Decorate the name even if a name is present.
     *                            This is to allow for docRefs with a name that is out of date with
     *                            the document it represents, e.g. after a rename.
     * @param requiredPermissions A list of permission names that the user must hold all of on the document.
     *                            If they do not then a {@link stroom.util.shared.PermissionException} will be
     *                            thrown. If null or empty, no permission check will be performed.
     * @return A fully populated {@link DocRef}.
     */
    DocRef decorate(final DocRef docRef,
                    final boolean force,
                    final Set<DocumentPermission> requiredPermissions);
}
