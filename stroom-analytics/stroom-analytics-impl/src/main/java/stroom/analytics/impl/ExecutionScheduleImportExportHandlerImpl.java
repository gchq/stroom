/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.Message;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import com.google.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExecutionScheduleImportExportHandlerImpl implements ImportExportActionHandler, NonExplorerDocRefProvider {
    private final Serialiser2<ExecutionSchedule> delegate;
    private final ExecutionScheduleDao executionScheduleDao;
    private final DocRefInfoService docRefInfoService;

    @Inject
    public ExecutionScheduleImportExportHandlerImpl(final Serialiser2Factory serialiser2Factory,
                                                    final ExecutionScheduleDao executionScheduleDao,
                                                    final DocRefInfoService docRefInfoService) {
        this.delegate = serialiser2Factory.createSerialiser(ExecutionSchedule.class);
        this.executionScheduleDao = executionScheduleDao;
        this.docRefInfoService = docRefInfoService;
    }

    private Optional<ExecutionSchedule> findExistingSchedule(final ExecutionSchedule importedSchedule,
                                                             final DocRef owningDoc) {
        // Find existing schedule based on owning doc and id
        final ExecutionScheduleRequest request = ExecutionScheduleRequest.builder()
                .ownerDocRef(owningDoc)
                .build();
        final ResultPage<ExecutionSchedule> page = executionScheduleDao.fetchExecutionSchedule(request);
        return page.getValues().stream()
                .filter(s -> Objects.equals(s.getUuid(), importedSchedule.getUuid()))
                .findFirst();
    }

    private Optional<ExecutionSchedule> findExistingSchedule(final DocRef pseudoDocRef) {
        return executionScheduleDao.fetchScheduleByUuid(pseudoDocRef.getUuid());
    }

    // ---------------------------------------------------------------------
    // START OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    @Override
    public DocRef importDocument(final DocRef pseudoDocRef,
            final ImportExportDocument importExportDocument,
            final ImportState importState,
            final ImportSettings importSettings) {
        final ImportExportAsset importExportAsset = importExportDocument.getExtAsset("meta");
        if (importExportAsset == null) {
            importState.addMessage(Severity.ERROR, "No meta data found for " + pseudoDocRef);
            return pseudoDocRef; // return un-imported docref
        }

        try {
            final ExecutionSchedule importedSchedule = delegate.read(importExportAsset);

            if (importedSchedule != null) {
                final DocRef owningDoc = importedSchedule.getOwningDoc();

                if (importSettings.getImportMode() == ImportMode.CREATE_CONFIRMATION) {
                    if (findExistingSchedule(importedSchedule, owningDoc).isPresent()) {
                        importState.setState(ImportState.State.UPDATE);
                    } else {
                        importState.setState(ImportState.State.NEW);
                    }
                } else if (!importSettings.getImportMode().equals(ImportMode.IGNORE_CONFIRMATION)) {
                    final Optional<ExecutionSchedule> existingScheduleOpt = findExistingSchedule(importedSchedule,
                            owningDoc);

                    if (existingScheduleOpt.isPresent()) {
                        final ExecutionSchedule existingSchedule = existingScheduleOpt.get();

                        final ExecutionSchedule updatedSchedule = importedSchedule.copy()
                                .uuid(existingSchedule.getUuid()) // preserve local UUID
                                .build();
                        executionScheduleDao.updateExecutionSchedule(updatedSchedule);
                    } else {
                        executionScheduleDao.createExecutionSchedule(importedSchedule);
                    }
                }
            }
        } catch (final IOException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
        }

        return pseudoDocRef;
    }

    @Override
    public ImportExportDocument exportDocument(final DocRef docRef,
            final boolean omitAuditFields,
            final List<Message> messageList) {
        try {
            final Optional<ExecutionSchedule> scheduleOpt = executionScheduleDao.fetchScheduleByUuid(docRef.getUuid());
            if (scheduleOpt.isPresent()) {
                final DocRef owningDoc = docRefInfoService.decorate(scheduleOpt.get().getOwningDoc());
                final ExecutionSchedule schedule = scheduleOpt.get().copy().owningDoc(owningDoc).build();
                return delegate.write(schedule);
            }
        } catch (final IOException e) {
            messageList.add(new Message(Severity.ERROR,
                    "Unable to export schedule " + docRef + ": " + e.getMessage()));
        }
        return null;
    }

    @Override
    public Set<DocRef> listDocuments() {
        final ExecutionScheduleRequest request = ExecutionScheduleRequest.builder().build();
        final ResultPage<ExecutionSchedule> page = executionScheduleDao.fetchExecutionSchedule(request);
        return page.getValues().stream()
                .map(s -> new DocRef(ExecutionSchedule.ENTITY_TYPE, s.getUuid(), s.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getType() {
        return ExecutionSchedule.ENTITY_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    // ---------------------------------------------------------------------
    // END OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF HasDependencies
    // ---------------------------------------------------------------------

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        //TODO 8192 check what this does, does it need implementing.
        return null;
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        //TODO 8192 check what this does, does it need implementing.
        return null;
    }

    @Override
    public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {
    }

    // ---------------------------------------------------------------------
    // END OF HasDependencies
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF NonExplorerDocRefProvider
    // ---------------------------------------------------------------------

    @Override
    public DocRef getOwnerDocument(final DocRef docRef, final ImportExportDocument importExportDocument) {
        if (importExportDocument != null) {
            try {
                final ImportExportAsset asset = importExportDocument.getExtAsset("meta");
                if (asset != null) {
                    final ExecutionSchedule schedule = delegate.read(asset);
                    if (schedule != null) {
                        return schedule.getOwningDoc();
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException("Error reading execution schedule meta data", e);
            }
        }
        return null;
    }

    @Override
    public DocRef findNearestExplorerDocRef(final DocRef pseudoDocRef) {
        if (pseudoDocRef != null && ExecutionSchedule.ENTITY_TYPE.equals(pseudoDocRef.getType())) {
            final Optional<ExecutionSchedule> scheduleOpt = findExistingSchedule(pseudoDocRef);
            if (scheduleOpt.isPresent()) {
                return scheduleOpt.get().getOwningDoc();
            }
        }
        return null;
    }

    @Override
    public String findNameOfDocRef(final DocRef docRef) {
        return docRef.getName();
    }

    @Override
    public DocRefInfo info(final DocRef pseudoDocRef) {
        try {
            final Optional<ExecutionSchedule> scheduleOpt = findExistingSchedule(pseudoDocRef);
            if (scheduleOpt.isPresent()) {
                return DocRefInfo.builder()
                    .docRef(pseudoDocRef)
                    .otherInfo(scheduleOpt.get().getOwningDoc().getName() + " - " + pseudoDocRef.getName())
                    .build();
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return DocRefInfo.builder().docRef(pseudoDocRef).build();
    }

    // ---------------------------------------------------------------------
    // END OF NonExplorerDocRefProvider
    // ---------------------------------------------------------------------
}
