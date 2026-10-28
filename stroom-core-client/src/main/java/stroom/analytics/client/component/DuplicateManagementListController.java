/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckResource;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// A rule's duplicate check rows ([DuplicateManagementList]), a page at a time; those selected can
/// be deleted, unless the rule is read only.
public class DuplicateManagementListController extends Controller {

    private static final DuplicateCheckResource DUPLICATE_CHECK_RESOURCE =
            GWT.create(DuplicateCheckResource.class);

    private final DuplicateManagementList view;
    private final RestFactory restFactory;
    private final FindDuplicateCheckCriteria criteria;
    private final RestDataProvider<DuplicateCheckRow, ResultPage<DuplicateCheckRow>> dataProvider;
    private boolean initialised;
    private boolean readOnly = true;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds and deletes the rows.
    @Inject
    public DuplicateManagementListController(final EventBus eventBus,
                                             final DuplicateManagementList view,
                                             final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setOnDelete(this::onDelete);
        view.setOnSelectionChange(this::enableButtons);

        criteria = new FindDuplicateCheckCriteria();
        //noinspection Convert2Diamond // cos GWT
        dataProvider = new RestDataProvider<DuplicateCheckRow, ResultPage<DuplicateCheckRow>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<DuplicateCheckRow>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                if (criteria.getAnalyticDocUuid() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    restFactory
                            .create(DUPLICATE_CHECK_RESOURCE)
                            .method(res -> res.find(criteria))
                            .onSuccess(result -> {
                                view.setColumns(result.getColumnNames());
                                dataConsumer.accept(result.getResultPage());
                            })
                            .onFailure(errorHandler)
                            .owner(DuplicateManagementListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            }
        };
    }

    private void enableButtons() {
        // Deleting rows changes what the rule will notify on, so isn't allowed if the document is read only
        view.setDeleteEnabled(!readOnly && NullSafe.hasItems(view.getSelectedItems()));
    }

    /// Shows the duplicate check rows for a rule.
    ///
    /// @param docRef   The rule to show the duplicate check rows for.
    public void read(final DocRef docRef) {
        // Read only as its document is
        this.readOnly = context(Contexts.READ_ONLY);
        enableButtons();
        criteria.setAnalyticDocUuid(docRef.getUuid());
        refresh();
    }

    /// Fetches the rows again; the first time, starts the list.
    public void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    private void onDelete() {
        if (readOnly) {
            return;
        }
        final List<DuplicateCheckRow> selected = view.getSelectedItems();
        if (NullSafe.hasItems(selected)) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected row" +
                                    (selected.size() > 1
                                            ? "s"
                                            : "") +
                                    "?",
                    result -> {
                        if (result) {
                            final DeleteDuplicateCheckRequest request =
                                    new DeleteDuplicateCheckRequest(criteria.getAnalyticDocUuid(), selected);
                            restFactory
                                    .create(DUPLICATE_CHECK_RESOURCE)
                                    .method(res -> res.delete(request))
                                    .onSuccess(r -> refresh())
                                    .owner(this)
                                    .taskMonitorFactory(view.getTaskMonitor())
                                    .exec();
                        }
                    });
        }
    }
}
