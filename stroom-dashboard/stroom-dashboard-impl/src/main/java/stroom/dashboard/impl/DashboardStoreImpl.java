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
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
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

@Singleton
class DashboardStoreImpl
        extends AbstractDocumentStore<DashboardDoc>
        implements DashboardStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardStoreImpl.class);

    private static final String VERSION_7_2_0 = Version.of(7, 2, 0).toString();

    private static final String TEMPLATE_FILE = "DashboardTemplate.json";

    private final DashboardSerialiser serialiser;
    private final SecurityContext securityContext;

    private DashboardConfig template;

    @Inject
    DashboardStoreImpl(final StoreFactory storeFactory,
                       final DashboardSerialiser serialiser,
                       final SecurityContext securityContext) {
        super(storeFactory,
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

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = getStore().createDocument(name);

        // Create a dashboard from a template.

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final DashboardDoc dashboardDoc = getStore()
                    .readDocument(docRef)
                    .copy()
                    .dashboardConfig(getTemplate())
                    .build();
            getStore().writeDocument(dashboardDoc);
        });
        return docRef;
    }

    @Override
    protected DependencyRemapFunction<DashboardDoc> getDependencyRemapFunction() {
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
}
