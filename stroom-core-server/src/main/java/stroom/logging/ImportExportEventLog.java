/*
 * Copyright 2016 Crown Copyright
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
import org.springframework.stereotype.Component;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.importexport.shared.ExportConfigAction;
import stroom.importexport.shared.ImportConfigAction;
import stroom.security.Insecure;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;
import java.util.List;

@Component
@Insecure
public class ImportExportEventLog {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ImportExportEventLog.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;
    @Resource
    private FolderService folderService;

    public void export(final ExportConfigAction exportDataAction) {
        try {
            final Event event = eventLoggingService.createAction("ExportConfig", "Exporting Configuration");

            final Criteria criteria = new Criteria();
            criteria.setType("Configuration");
            appendCriteria(criteria, exportDataAction.getCriteria());

            final MultiObject multiObject = new MultiObject();
            multiObject.getObjects().add(criteria);

            final Export exp = new Export();
            exp.setSource(multiObject);

            event.getEventDetail().setExport(exp);

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    public void _import(final ImportConfigAction importDataAction) {
        try {
            final List<EntityActionConfirmation> confirmList = importDataAction.getConfirmList();
            if (confirmList != null && confirmList.size() > 0) {
                for (final EntityActionConfirmation confirmation : confirmList) {
                    try {
                        final Event event = eventLoggingService.createAction("ImportConfig", "Importing Configuration");

                        final event.logging.Object object = new event.logging.Object();
                        object.setType(confirmation.getEntityType());
                        object.setId(confirmation.getPath());
                        object.setName(confirmation.getPath());
                        object.getData().add(EventLoggingUtil.createData("ImportAction",
                                confirmation.getEntityAction().getDisplayValue()));

                        final MultiObject multiObject = new MultiObject();
                        multiObject.getObjects().add(object);

                        final Import imp = new Import();
                        imp.setSource(multiObject);

                        event.getEventDetail().setImport(imp);

                        eventLoggingService.log(event);
                    } catch (final Exception e) {
                        LOGGER.error(e, e);
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }

    private void appendCriteria(final Criteria parent, final FindFolderCriteria criteria) {
        if (criteria != null && criteria.getFolderIdSet() != null && criteria.getFolderIdSet().size() > 0) {
            final Query query = new Query();
            parent.setQuery(query);

            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);

            final Or or = new Or();
            advanced.getAdvancedQueryItems().add(or);

            for (final Long folderId : criteria.getFolderIdSet()) {
                final Folder folder = folderService.loadById(folderId);
                final event.logging.Term term = new event.logging.Term();
                term.setName("Folder");
                term.setCondition(TermCondition.EQUALS);
                term.setValue(folder.getName());

                or.getAdvancedQueryItems().add(term);
            }
        }
    }
}
