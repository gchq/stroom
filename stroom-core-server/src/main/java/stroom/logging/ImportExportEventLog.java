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

package stroom.logging;

import event.logging.BaseAdvancedQueryOperator.Or;
import event.logging.Criteria;
import event.logging.Event;
import event.logging.Export;
import event.logging.Import;
import event.logging.MultiObject;
import event.logging.Query;
import event.logging.Query.Advanced;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.shared.DocRefs;
import stroom.importexport.shared.ExportConfigAction;
import stroom.importexport.shared.ImportConfigAction;
import stroom.importexport.shared.ImportState;
import stroom.security.Insecure;

import javax.annotation.Resource;
import java.util.List;

@Component
@Insecure
public class ImportExportEventLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportEventLog.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;

    public void export(final ExportConfigAction exportDataAction) {
        try {
            final Event event = eventLoggingService.createAction("ExportConfig", "Exporting Configuration");

            final Criteria criteria = new Criteria();
            criteria.setType("Configuration");
            appendCriteria(criteria, exportDataAction.getDocRefs());

            final MultiObject multiObject = new MultiObject();
            multiObject.getObjects().add(criteria);

            final Export exp = new Export();
            exp.setSource(multiObject);

            event.getEventDetail().setExport(exp);

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error("Unable to export event!", e);
        }
    }

    public void _import(final ImportConfigAction importDataAction) {
        try {
            final List<ImportState> confirmList = importDataAction.getConfirmList();
            if (confirmList != null && confirmList.size() > 0) {
                for (final ImportState confirmation : confirmList) {
                    try {
                        final Event event = eventLoggingService.createAction("ImportConfig", "Importing Configuration");

                        String state = "Error";
                        if (confirmation.getState() != null) {
                            state = confirmation.getState().getDisplayValue();
                        }

                        final event.logging.Object object = new event.logging.Object();
                        object.setType(confirmation.getDocRef().getType());
                        object.setId(confirmation.getDocRef().getUuid());
                        object.setName(confirmation.getSourcePath());
                        object.getData().add(EventLoggingUtil.createData("ImportAction", state));

                        final MultiObject multiObject = new MultiObject();
                        multiObject.getObjects().add(object);

                        final Import imp = new Import();
                        imp.setSource(multiObject);

                        event.getEventDetail().setImport(imp);

                        eventLoggingService.log(event);
                    } catch (final Exception e) {
                        LOGGER.error("Unable to import event!", e);
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Unable to import event!", e);
        }
    }

    private void appendCriteria(final Criteria parent, final DocRefs docRefs) {
        if (docRefs != null) {
            final Query query = new Query();
            parent.setQuery(query);

            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);

            final Or or = new Or();
            advanced.getAdvancedQueryItems().add(or);

            docRefs.forEach(docRef -> {
                final event.logging.Term term = new event.logging.Term();
                term.setName(docRef.getType());
                term.setCondition(TermCondition.EQUALS);
                term.setValue(docRef.getUuid());

                or.getAdvancedQueryItems().add(term);
            });
        }
    }
}
