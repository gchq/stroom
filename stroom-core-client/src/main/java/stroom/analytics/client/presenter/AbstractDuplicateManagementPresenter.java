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

package stroom.analytics.client.presenter;

import stroom.analytics.client.component.DuplicateManagementListController;
import stroom.analytics.client.presenter.AbstractDuplicateManagementPresenter.DuplicateManagementView;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.docref.DocRef;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.entity.client.presenter.DocPresenter;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractDuplicateManagementPresenter<D extends AbstractAnalyticRuleDoc>
        extends DocPresenter<DuplicateManagementView, D> {

    private final DuplicateManagementListController duplicateManagementList;

    @Inject
    public AbstractDuplicateManagementPresenter(final EventBus eventBus,
                                                final DuplicateManagementView view,
                                                final DuplicateManagementListController
                                                        duplicateManagementList) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);
        own(duplicateManagementList);
        view.setUiHandlers(this);
        this.duplicateManagementList = duplicateManagementList;
        view.setListView(duplicateManagementList);
    }

    @Override
    protected void onRead(final DocRef docRef, final D document) {
        final DuplicateNotificationConfig duplicateNotificationConfig = document.getDuplicateNotificationConfig();
        getView().setRememberNotifications(duplicateNotificationConfig.isRememberNotifications());
        getView().setSuppressDuplicateNotifications(duplicateNotificationConfig.isSuppressDuplicateNotifications());
        getView().setChooseColumns(duplicateNotificationConfig.isChooseColumns());
        getView().setColumns(String.join(", ", duplicateNotificationConfig.getColumnNames()));
        duplicateManagementList.read(docRef);
    }

    protected DuplicateNotificationConfig writeDuplicateNotificationConfig() {
        final String[] arr = NullSafe.string(getView().getColumns())
                .split(",");
        final List<String> columns = Arrays.stream(arr)
                .map(String::trim)
                .filter(NullSafe::isNonEmptyString)
                .collect(Collectors.toList());
        return new DuplicateNotificationConfig(getView().isRememberNotifications(),
                getView().isSuppressDuplicateNotifications(),
                getView().isChooseColumns(),
                columns);
    }


    // --------------------------------------------------------------------------------


    public interface DuplicateManagementView extends View, HasUiHandlers<ChangeUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        void setRememberNotifications(boolean rememberNotifications);

        boolean isRememberNotifications();

        void setSuppressDuplicateNotifications(boolean suppressDuplicateNotifications);

        boolean isSuppressDuplicateNotifications();

        void setChooseColumns(boolean chooseColumns);

        boolean isChooseColumns();

        void setColumns(String columns);

        String getColumns();

        void setListView(IsWidget view);
    }

}
