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
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Version;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
class DashboardStoreImpl implements DashboardStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardStoreImpl.class);

    private static final String VERSION_7_2_0 = Version.of(7, 2, 0).toString();

    private static final String TEMPLATE_FILE = "DashboardTemplate.json";

    private final Store<DashboardDoc> store;
    private final DashboardSerialiser serialiser;
    private final SecurityContext securityContext;

    private DashboardConfig template;

    @Inject
    DashboardStoreImpl(final StoreFactory storeFactory,
                       final DashboardSerialiser serialiser,
                       final SecurityContext securityContext) {
        this.store = storeFactory.createStore(
                serialiser,
                DashboardDoc.TYPE,
                DashboardDoc::builder,
                DashboardDoc::copy);
        this.serialiser = serialiser;
        this.securityContext = securityContext;
    }

    private DashboardConfig getTemplate() {
        if (template == null) {
            try (final InputStream is = getClass().getResourceAsStream(TEMPLATE_FILE)) {
                if (is != null) {
                    final byte[] bytes = is.readAllBytes();
                    template = serialiser.getDashboardConfigFromJson(bytes)
                            .copy()
                            .modelVersion(VERSION_7_2_0)
                            .designMode(true)
                            .build();
                } else {
                    LOGGER.error("Error reading dashboard template as template not found: " + TEMPLATE_FILE);
                }
            } catch (final IOException e) {
                LOGGER.error("Error reading dashboard template from file", e);
            }
        }
        return template;
    }

    // ---------------------------------------------------------------------
    // START OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = store.createDocument(name);

        // Create a dashboard from a template.

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final DashboardDoc dashboardDoc = store.readDocument(docRef).copy().dashboardConfig(getTemplate()).build();
            store.writeDocument(dashboardDoc);
        });
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    // ---------------------------------------------------------------------
    // END OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF HasDependencies
    // ---------------------------------------------------------------------

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

    private DependencyRemapFunction<DashboardDoc> createMapper() {
        return (doc, dependencyRemapper) -> {
            DashboardDoc updated = doc;
            if (updated.getDashboardConfig() != null) {
                final List<ComponentConfig> components = updated.getDashboardConfig().getComponents();
                if (!NullSafe.isEmptyCollection(components)) {
                    final List<ComponentConfig> newComponents = new ArrayList<>();

                    components.forEach(componentConfig -> {
                        ComponentSettings componentSettings = componentConfig.getSettings();
                        if (componentSettings != null) {
                            switch (componentSettings) {
                                case final QueryComponentSettings queryComponentSettings ->
                                        componentSettings = remapQueryComponentSettings(queryComponentSettings,
                                                dependencyRemapper);
                                case final TableComponentSettings tableComponentSettings ->
                                        componentSettings = remapTableComponentSettings(tableComponentSettings,
                                                dependencyRemapper);
                                case final VisComponentSettings visComponentSettings ->
                                        componentSettings = remapVisComponentSettings(visComponentSettings,
                                                dependencyRemapper);
                                case final TextComponentSettings textComponentSettings ->
                                        componentSettings = remapTextComponentSettings(textComponentSettings,
                                                dependencyRemapper);
                                default -> {
                                }
                            }
                        }

                        final ComponentConfig newConfig = componentConfig
                                .copy()
                                .settings(componentSettings)
                                .build();
                        newComponents.add(newConfig);
                    });

                    updated = updated
                            .copy()
                            .dashboardConfig(doc
                                    .getDashboardConfig()
                                    .copy()
                                    .components(newComponents)
                                    .build())
                            .build();
                }
            }
            return updated;
        };
    }

    private QueryComponentSettings remapQueryComponentSettings(final QueryComponentSettings queryComponentSettings,
                                                               final DependencyRemapper dependencyRemapper) {
        final QueryComponentSettings.Builder builder = queryComponentSettings.copy();

        builder.dataSource(dependencyRemapper.remap(queryComponentSettings.getDataSource()));

        if (queryComponentSettings.getExpression() != null) {
            builder.expression(dependencyRemapper.remapExpression(queryComponentSettings.getExpression()));
        }

        return builder.build();
    }

    private TableComponentSettings remapTableComponentSettings(final TableComponentSettings tableComponentSettings,
                                                               final DependencyRemapper dependencyRemapper) {
        final TableComponentSettings.Builder builder = tableComponentSettings.copy();

        final String uuid = NullSafe.get(tableComponentSettings.getExtractionPipeline(), DocRef::getUuid);
        if (NullSafe.isNonEmptyString(uuid)) {
            builder.extractionPipeline(dependencyRemapper.remap(tableComponentSettings.getExtractionPipeline()));
        }

        return builder.build();
    }

    private VisComponentSettings remapVisComponentSettings(final VisComponentSettings visComponentSettings,
                                                           final DependencyRemapper dependencyRemapper) {
        final VisComponentSettings.Builder builder = visComponentSettings.copy();

        builder.visualisation(dependencyRemapper.remap(visComponentSettings.getVisualisation()));

        return builder.build();
    }

    private TextComponentSettings remapTextComponentSettings(final TextComponentSettings textComponentSettings,
                                                             final DependencyRemapper dependencyRemapper) {
        final TextComponentSettings.Builder builder = textComponentSettings.copy();
        builder.pipeline(dependencyRemapper.remap(textComponentSettings.getPipeline()));
        return builder.build();
    }

    // ---------------------------------------------------------------------
    // END OF HasDependencies
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF DocumentActionHandler
    // ---------------------------------------------------------------------

    @Override
    public DashboardDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public DashboardDoc writeDocument(final DashboardDoc document) {
        return store.writeDocument(document);
    }

    // ---------------------------------------------------------------------
    // END OF DocumentActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return store.importDocument(docRef, dataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    // ---------------------------------------------------------------------
    // END OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }
}
