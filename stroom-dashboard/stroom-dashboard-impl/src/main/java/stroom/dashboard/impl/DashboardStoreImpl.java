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

package stroom.dashboard.impl;

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.util.shared.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Singleton
class DashboardStoreImpl implements DashboardStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardStoreImpl.class);

    private final Store<DashboardDoc> store;
    private final DashboardSerialiser serialiser;

    private DashboardConfig template;

    @Inject
    DashboardStoreImpl(final StoreFactory storeFactory,
                       final DashboardSerialiser serialiser) {
        this.store = storeFactory.createStore(serialiser, DashboardDoc.DOCUMENT_TYPE, DashboardDoc.class);
        this.serialiser = serialiser;
    }

    private DashboardConfig getTemplate() {
        if (template == null) {
            try (final InputStream is = getClass().getResourceAsStream("DashboardTemplate.json")) {
                final byte[] bytes = is.readAllBytes();
                template = serialiser.getDashboardConfigFromJson(bytes);
            } catch (final IOException e) {
                LOGGER.error("Error reading dashboard template from file", e);
            }
        }
        return template;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = store.createDocument(name);

        // Create a dashboard from a template.
        final DashboardDoc dashboardDoc = store.readDocument(docRef);
        dashboardDoc.setDashboardConfig(getTemplate());
        store.writeDocument(dashboardDoc);
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef, final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(docRef.getName(), existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final String uuid) {
        return store.moveDocument(uuid);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        return store.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        store.deleteDocument(uuid);
    }

    @Override
    public DocRefInfo info(String uuid) {
        return store.info(uuid);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(7, DashboardDoc.DOCUMENT_TYPE, DashboardDoc.DOCUMENT_TYPE);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(createMapper());
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, createMapper());
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, createMapper());
    }

    private BiConsumer<DashboardDoc, DependencyRemapper> createMapper() {
        return (doc, dependencyRemapper) -> {
            if (doc.getDashboardConfig() != null) {
                final List<ComponentConfig> components = doc.getDashboardConfig().getComponents();
                if (components != null && components.size() > 0) {
                    components.forEach(componentConfig -> {
                        final ComponentSettings componentSettings = componentConfig.getSettings();
                        if (componentSettings != null) {
                            if (componentSettings instanceof QueryComponentSettings) {
                                final QueryComponentSettings queryComponentSettings = (QueryComponentSettings) componentSettings;
                                remapQueryComponentSettings(queryComponentSettings, dependencyRemapper);

                            } else if (componentSettings instanceof TableComponentSettings) {
                                final TableComponentSettings tableComponentSettings = (TableComponentSettings) componentSettings;
                                remapTableComponentSettings(tableComponentSettings, dependencyRemapper);

                            } else if (componentSettings instanceof VisComponentSettings) {
                                final VisComponentSettings visComponentSettings = (VisComponentSettings) componentSettings;
                                remapVisComponentSettings(visComponentSettings, dependencyRemapper);

                            } else if (componentSettings instanceof TextComponentSettings) {
                                final TextComponentSettings textComponentSettings = (TextComponentSettings) componentSettings;
                                remapTextComponentSettings(textComponentSettings, dependencyRemapper);
                            }
                        }
                    });
                }
            }
        };
    }

    private void remapQueryComponentSettings(final QueryComponentSettings queryComponentSettings, final DependencyRemapper dependencyRemapper) {
        queryComponentSettings.setDataSource(dependencyRemapper.remap(queryComponentSettings.getDataSource()));

        if (queryComponentSettings.getExpression() != null) {
            dependencyRemapper.remapExpression(queryComponentSettings.getExpression());
        }
    }

    private void remapTableComponentSettings(final TableComponentSettings tableComponentSettings, final DependencyRemapper dependencyRemapper) {
        if (tableComponentSettings.getExtractionPipeline() != null &&
                tableComponentSettings.getExtractionPipeline().getUuid() != null &&
                tableComponentSettings.getExtractionPipeline().getUuid().length() > 0) {
            tableComponentSettings.setExtractionPipeline(dependencyRemapper.remap(tableComponentSettings.getExtractionPipeline()));
        }
    }

    private void remapVisComponentSettings(final VisComponentSettings visComponentSettings, final DependencyRemapper dependencyRemapper) {
        visComponentSettings.setVisualisation(dependencyRemapper.remap(visComponentSettings.getVisualisation()));

        if (visComponentSettings.getTableSettings() != null) {
            remapTableComponentSettings(visComponentSettings.getTableSettings(), dependencyRemapper);
        }
    }

    private void remapTextComponentSettings(final TextComponentSettings textComponentSettings, final DependencyRemapper dependencyRemapper) {
        textComponentSettings.setPipeline(dependencyRemapper.remap(textComponentSettings.getPipeline()));
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DashboardDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public DashboardDoc writeDocument(final DashboardDoc document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public ImpexDetails importDocument(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        return store.importDocument(docRef, dataMap, importState, importMode);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    @Override
    public String getType() {
        return DashboardDoc.DOCUMENT_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
