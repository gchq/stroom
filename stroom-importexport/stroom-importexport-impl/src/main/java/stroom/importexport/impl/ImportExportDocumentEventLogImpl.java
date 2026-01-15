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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.security.api.SecurityContext;

import event.logging.CreateEventAction;
import event.logging.ExportEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ImportExportDocumentEventLogImpl implements ImportExportDocumentEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportDocumentEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;
    private final SecurityContext securityContext;

    @Inject
    ImportExportDocumentEventLogImpl(final StroomEventLoggingService eventLoggingService,
                                     final SecurityContext securityContext) {
        this.eventLoggingService = eventLoggingService;
        this.securityContext = securityContext;
    }

    @Override
    public void importDocument(final String type, final String uuid, final String name, final Exception e) {
        // TODO @AT Think this is a dup of the logging done in ContentResource
        securityContext.insecure(() -> {
            try {
                eventLoggingService.log(
                        "Import",
                        buildEventDescription("Importing", type, name),
                        CreateEventAction.builder()
                                .addObject(OtherObject.builder()
                                        .withId(uuid)
                                        .withType(type)
                                        .withName(name)
                                        .build())
                                .withOutcome(EventLoggingUtil.createOutcome(e))
                                .build());

            } catch (final RuntimeException ex) {
                LOGGER.error("Unable to create event!", ex);
            }
        });
    }

    @Override
    public void exportDocument(final DocRef document, final Exception e) {
        try {
            eventLoggingService.log(
                    "ExportDocument",
                    buildEventDescription("Exporting Stroom Config Item",
                            document.getType(), document.getName()),
                    ExportEventAction.builder()
                            .withSource(MultiObject.builder()
                                    .addObject(OtherObject.builder()
                                            .withId(document.getUuid())
                                            .withType(document.getType())
                                            .withName(document.getName())
                                            .build())
                                    .build())
                            .withOutcome(EventLoggingUtil.createOutcome(e))
                            .build());

        } catch (final RuntimeException ex) {
            LOGGER.error("Unable to create event!", ex);
        }
    }

    private String buildEventDescription(final String description,
                                         final String entityType,
                                         final String entityName) {
        return description + " " + entityType + " \"" + entityName + "\"";
    }
}
