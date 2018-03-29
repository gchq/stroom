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

package stroom.importexport;

import event.logging.Event;
import event.logging.Export;
import event.logging.MultiObject;
import event.logging.Object;
import event.logging.ObjectOutcome;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.logging.StroomEventLoggingService;
import stroom.query.api.v2.DocRef;
import stroom.security.Insecure;

import javax.inject.Inject;

@Insecure
class ImportExportDocumentEventLogImpl implements ImportExportDocumentEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportDocumentEventLogImpl.class);

    private final StroomEventLoggingService eventLoggingService;

    @Inject
    ImportExportDocumentEventLogImpl(final StroomEventLoggingService eventLoggingService) {
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void importDocument(final String type, final String uuid, final String name, final Exception e) {
        try {
            final Event event = createAction("Import", "Importing", type, name);
            final ObjectOutcome objectOutcome = new ObjectOutcome();
            event.getEventDetail().setCreate(objectOutcome);

            final Object object = new Object();
            object.setType(type);
            object.setId(uuid);
            object.setName(name);

            objectOutcome.getObjects().add(object);
            objectOutcome.setOutcome(EventLoggingUtil.createOutcome(e));
            eventLoggingService.log(event);
        } catch (final RuntimeException ex) {
            LOGGER.error("Unable to create event!", ex);
        }
    }

    @Override
    public void exportDocument(final DocRef document, final Exception e) {
        try {
            final Event event = createAction("Export", "Exporting", document.getType(), document.getName());

            final Object object = new Object();
            object.setType(document.getType());
            object.setId(document.getUuid());
            object.setName(document.getName());

            final MultiObject multiObject = new MultiObject();
            multiObject.getObjects().add(object);

            final Export export = new Export();
            export.setSource(multiObject);
            export.setOutcome(EventLoggingUtil.createOutcome(e));

            event.getEventDetail().setExport(export);

            eventLoggingService.log(event);
        } catch (final RuntimeException ex) {
            LOGGER.error("Unable to create event!", ex);
        }
    }

    private Event createAction(final String typeId, final String description, final String entityType,
                               final String entityName) {
        final String desc = description + " " + entityType + " \"" + entityName;
        return eventLoggingService.createAction(typeId, desc);
    }
}
