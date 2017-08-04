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

package stroom.dashboard.server.logging;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Criteria;
import event.logging.Event;
import event.logging.Export;
import event.logging.Import;
import event.logging.MultiObject;
import event.logging.ObjectOutcome;
import event.logging.Query;
import event.logging.Query.Advanced;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.FolderService;
import stroom.feed.shared.Feed;
import stroom.security.Insecure;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Insecure
public class StreamEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamEventLog.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;
    @Resource
    private FolderService folderService;
    @Resource
    private StreamStore streamStore;

    public void importStream(final Date receivedDate, final String feedName, final String path, final Throwable th) {
        try {
            final Event event = eventLoggingService.createAction("Data Upload", "Data uploaded to \"" + feedName + "\"");

            final event.logging.Object object = new event.logging.Object();
            object.setType("Stream");
            object.getData().add(EventLoggingUtil.createData("Path", path));
            object.getData().add(EventLoggingUtil.createData("Feed", feedName));

            final MultiObject multiObject = new MultiObject();
            multiObject.getObjects().add(object);

            final Import imp = new Import();
            imp.setSource(multiObject);
            imp.setOutcome(EventLoggingUtil.createOutcome(th));

            event.getEventDetail().setImport(imp);

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to import stream!", e);
        }
    }

    public void viewStream(final Stream stream, final Feed feed, final StreamType streamType, final Throwable th) {
        try {
            if (stream != null) {
                final Event event = eventLoggingService.createAction("View", "Viewing Stream");
                final ObjectOutcome objectOutcome = new ObjectOutcome();
                event.getEventDetail().setView(objectOutcome);
                objectOutcome.getObjects().add(createStreamObject(stream, feed, streamType));
                objectOutcome.setOutcome(EventLoggingUtil.createOutcome(th));
                eventLoggingService.log(event);
            }
        } catch (final Exception e) {
            LOGGER.error("Unable to view stream!", e);
        }
    }

    public void exportStream(final FindStreamCriteria findStreamCriteria, final Throwable th) {
        try {
            if (findStreamCriteria != null) {
                final Event event = eventLoggingService.createAction("ExportData", "Exporting Data");

                final Criteria criteria = new Criteria();
                criteria.setType("Data");
                criteria.setQuery(createQuery(findStreamCriteria));

                final MultiObject multiObject = new MultiObject();
                multiObject.getObjects().add(criteria);

                final Export exp = new Export();
                exp.setSource(multiObject);
                exp.setOutcome(EventLoggingUtil.createOutcome(th));

                event.getEventDetail().setExport(exp);

                eventLoggingService.log(event);
            }
        } catch (final Exception e) {
            LOGGER.error("Unable to export stream!", e);
        }
    }

    private Query createQuery(final FindStreamCriteria findStreamCriteria) {
        if (findStreamCriteria != null) {
            final And and = new And();
            streamStore.appendCriteria(and.getAdvancedQueryItems(), findStreamCriteria);

            final Advanced advanced = new Advanced();
            advanced.getAdvancedQueryItems().add(and);

            final Query query = new Query();
            query.setAdvanced(advanced);

            return query;
        }

        return null;
    }

    private event.logging.Object createStreamObject(final Stream stream, final Feed feed,
                                                    final StreamType streamType) {
        final event.logging.Object object = new event.logging.Object();
        object.setType("Stream");
        object.setId(String.valueOf(stream.getId()));
        if (feed != null) {
            object.getData().add(EventLoggingUtil.createData("Feed", feed.getName()));
        }
        if (streamType != null) {
            object.getData().add(EventLoggingUtil.createData("StreamType", streamType.getDisplayValue()));
        }

        return object;
    }
}
