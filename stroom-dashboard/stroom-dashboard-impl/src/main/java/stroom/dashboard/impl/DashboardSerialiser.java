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

import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DashboardSerialiser implements DocumentSerialiser2<DashboardDoc> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardSerialiser.class);

    private static final String JSON = "json";

    private final Serialiser2<DashboardDoc> delegate;
    private final Serialiser2<DashboardConfig> dashboardConfigSerialiser;

    @Inject
    public DashboardSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(DashboardDoc.class);
        this.dashboardConfigSerialiser = serialiser2Factory.createSerialiser(DashboardConfig.class);
    }

    @Override
    public DashboardDoc read(final ImportExportDocument importExportDocument) throws IOException {
        DashboardDoc document = delegate.read(importExportDocument);
        final ImportExportAsset jsonAsset = importExportDocument.getExtAsset(JSON);
        if (jsonAsset != null) {
            try {
                final DashboardConfig dashboardConfig = getDashboardConfigFromJson(jsonAsset);
                document = document.copy().dashboardConfig(dashboardConfig).build();
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }
        return document;
    }

    @Override
    public ImportExportDocument write(final DashboardDoc document) throws IOException {
        final DashboardConfig dashboardConfig = document.getDashboardConfig();
        final ImportExportDocument importExportDocument = delegate.write(document.copy().dashboardConfig(null).build());
        if (dashboardConfig != null) {
            final byte[] jsonBytes = dashboardConfigSerialiser.writeAsBytes(dashboardConfig);
            importExportDocument.addExtAsset(new ByteArrayImportExportAsset(JSON, jsonBytes));
        }
        return importExportDocument;
    }

    public DashboardConfig getDashboardConfigFromJson(final ImportExportAsset asset) throws IOException {
        return dashboardConfigSerialiser.read(asset);
    }

    public DashboardConfig getDashboardConfigFromJson(final byte[] data) throws IOException {
        // Wrap the data in an asset for the serialiser
        final ByteArrayImportExportAsset asset = new ByteArrayImportExportAsset("dummy", data);
        return dashboardConfigSerialiser.read(asset);
    }
}
