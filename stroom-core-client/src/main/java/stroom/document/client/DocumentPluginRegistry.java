/*
 * Copyright 2020 Crown Copyright
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

package stroom.document.client;

import stroom.core.client.TabPlugin;
import stroom.docref.DocRef;
import stroom.widget.tab.client.presenter.TabData;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class DocumentPluginRegistry {

    private final Map<String, TabPlugin> pluginMap = new HashMap<>();

    public void register(final String type, final TabPlugin plugin) {
        pluginMap.put(type, plugin);
    }

    public TabPlugin get(final String type) {
        return pluginMap.get(type);
    }

    public DocumentPlugin<?> getDocumentPlugin(final String type) {
        final TabPlugin plugin = pluginMap.get(type);
        if (plugin instanceof final DocumentPlugin<?> documentPlugin) {
            return documentPlugin;
        }
        return null;
    }

    /**
     * Get a plugin for a specific document type with type safety.
     * The caller must provide the document class to ensure type safety.
     */
    @SuppressWarnings("unchecked")
    public <D> DocumentPlugin<D> get(final String type, final Class<D> documentClass) {
        return (DocumentPlugin<D>) pluginMap.get(type);
    }

    /**
     * The DocRef of a tab that can actually be revealed in the explorer tree, else null.
     *
     * <p>Every content tab reports a non-null DocRef — {@code ContentTabPresenter} synthesises one
     * from the tab's type so a tab SESSION can be saved and restored — so a non-null DocRef is not
     * evidence that the tab is in the tree. The plugin registry is: it holds a
     * {@code DocumentPlugin} only for types the explorer can contain, whereas screen tabs
     * (Welcome, Monitoring, Administration) register a {@code ContentPlugin}. Client-side and
     * synchronous, so no fetch is needed.</p>
     */
    public DocRef getExplorerDocRef(final TabData tabData) {
        if (tabData instanceof final DocumentTabData documentTabData) {
            final DocRef docRef = documentTabData.getDocRef();
            if (docRef != null && getDocumentPlugin(docRef.getType()) != null) {
                return docRef;
            }
        }
        return null;
    }
}
