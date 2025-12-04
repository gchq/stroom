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

package stroom.dashboard.impl.script;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.script.shared.FetchLinkedScriptRequest;
import stroom.script.shared.ScriptDoc;
import stroom.script.shared.ScriptResource;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class ScriptResourceImpl implements ScriptResource {

    private final Provider<ScriptStore> scriptStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    ScriptResourceImpl(final Provider<ScriptStore> scriptStoreProvider,
                       final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.scriptStoreProvider = scriptStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public ScriptDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(scriptStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ScriptDoc update(final String uuid, final ScriptDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(scriptStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ScriptDoc.TYPE)
                .build();
    }

    @Override
    public List<ScriptDoc> fetchLinkedScripts(final FetchLinkedScriptRequest request) {
        return scriptStoreProvider.get().fetchLinkedScripts(request.getScript(), request.getLoadedScripts());
    }
}
