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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class PlanBDocStoreImpl
        extends AbstractDocumentStore<PlanBDoc>
        implements PlanBDocStore {

    private final SecurityContext securityContext;

    @Inject
    public PlanBDocStoreImpl(
            final StoreFactory storeFactory,
            final PlanBDocSerialiser serialiser,
            final SecurityContext securityContext) {
        super(storeFactory,
                serialiser,
                PlanBDoc.TYPE,
                PlanBDoc::builder,
                PlanBDoc::copy);
        this.securityContext = securityContext;
    }

    @Override
    public DocRef createDocument(final String name) {
        validateName(name);

        final DocRef created = getStore().createDocument(name);

        // Double-check the feed wasn't created elsewhere at the same time.
        if (checkDuplicateName(name, created)) {
            // Delete the newly created document as the key is duplicated.

            // Delete as a processing user to ensure we are allowed to delete the item as documents do not have
            // permissions added to them until after they are created in the store.
            securityContext.asProcessingUser(() -> getStore().deleteDocument(created));
            throwNameException(name);
        }

        PlanBDoc doc = getStore().readDocument(created);
        doc = doc.copy().stateType(StateType.TEMPORAL_STATE).build();
        getStore().writeDocument(doc);

        return created;
    }

    private void validateName(final String name) {
        if (!PlanBNameValidator.isValidName(name)) {
            throw new EntityServiceException("The state store key must match the pattern '" +
                                             PlanBNameValidator.getPattern() +
                                             "'");
        }
    }

    private void throwNameException(final String name) {
        throw new EntityServiceException("A state store named '" + name + "' already exists");
    }

    private boolean checkDuplicateName(final String name, final DocRef whitelistDocRef) {
        final List<DocRef> docRefs = list();
        for (final DocRef docRef : docRefs) {
            if (name.equals(docRef.getName()) &&
                (whitelistDocRef == null || !whitelistDocRef.equals(docRef))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        String newName = name;
        if (makeNameUnique) {
            newName = createUniqueName(name, getExistingNames());
        } else if (checkDuplicateName(name, null)) {
            throwNameException(name);
        }

        return getStore().copyDocument(docRef.getUuid(), newName);
    }

    private Set<String> getExistingNames() {
        return list()
                .stream()
                .map(DocRef::getName)
                .collect(Collectors.toSet());
    }

    static String createUniqueName(final String name, final Set<String> existingNames) {
        // Get a numbered suffix.
        final char[] chars = name.toCharArray();
        int index = -1;
        for (int i = chars.length - 1; i >= 0; i--) {
            final char c = chars[i];
            if (!Character.isDigit(c)) {
                index = i + 1;
                break;
            }
        }

        String prefix = name.substring(0, index);
        String suffix = name.substring(index);
        int num = 2;
        if (!suffix.isEmpty()) {
            num = Integer.parseInt(suffix) + 1;
        }

        for (int i = num; i < 10000; i++) {
            suffix = String.valueOf(i);
            final int maxPrefixLength = 48 - suffix.length();
            if (prefix.length() > maxPrefixLength) {
                prefix = prefix.substring(0, maxPrefixLength);
            }
            final String copyName = prefix + suffix;
            if (!existingNames.contains(copyName)) {
                return copyName;
            }
        }

        throw new EntityServiceException("Unable to make unique key for state store.");
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        validateName(name);

        // Check a state store doesn't already exist with this key.
        if (checkDuplicateName(name, docRef)) {
            throw new EntityServiceException("A state store named '" + name + "' already exists");
        }

        return super.renameDocument(docRef, name);
    }

    @Override
    public PlanBDoc writeDocument(final PlanBDoc document) {
        validateName(document.getName());
        return super.writeDocument(document);
    }
}
