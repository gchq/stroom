/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.LifespanInfo;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchTaskProgress;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Search Result Stores ([SearchResultStores]): the result stores on every node, the details of the
/// one chosen. Kept by the app and shown again.
public class SearchResultStoresController extends Controller {

    private final SearchResultStores view;
    private final ResultStoreListController resultStoreList;
    private final DateTimeFormatter dateTimeFormatter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param resultStoreList   The list of stores.
    /// @param dateTimeFormatter Formats the creation time in the user's preferences.
    @Inject
    public SearchResultStoresController(final EventBus eventBus,
                                        final SearchResultStores view,
                                        final ResultStoreListController resultStoreList,
                                        final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.resultStoreList = own(resultStoreList).attach(view.getList());
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
        own(resultStoreList.getSelectionModel().addSelectionHandler(event ->
                showDetails(resultStoreList.getSelectionModel().getSelected())));
    }

    /// Fetches the stores again and shows the dialog, owned by what owns this.
    public void show() {
        resultStoreList.refresh();
        ControllerDialog.show(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(1000, 600))
                .caption("Search Result Stores")
                .fire();
    }

    private void showDetails(final ResultStoreInfo resultStoreInfo) {
        final TableBuilder tb = new TableBuilder();
        if (resultStoreInfo != null) {
            tb
                    .row(TableCell.header("Store Details", 2))
                    .row("UUID", resultStoreInfo.getQueryKey().getUuid())
                    .row("Owner", NullSafe.get(resultStoreInfo.getOwner(), UserRef::toDisplayString))
                    .row("Creation Time", dateTimeFormatter.format(resultStoreInfo.getCreationTime()))
                    .row("Age", ModelStringUtil.formatDurationString(
                            System.currentTimeMillis() - resultStoreInfo.getCreationTime()))
                    .row("Node Name", resultStoreInfo.getNodeName())
                    .row("Store Size", ModelStringUtil.formatIECByteSizeString(resultStoreInfo.getStoreSize()))
                    .row("Complete", Boolean.toString(resultStoreInfo.isComplete()));
            final SearchTaskProgress taskProgress = resultStoreInfo.getTaskProgress();
            if (taskProgress != null) {
                tb.row("Task Info", taskProgress.getTaskInfo());
            }

            if (resultStoreInfo.getSearchRequestSource() != null) {
                final SearchRequestSource source = resultStoreInfo.getSearchRequestSource();
                tb.row(TableCell.header("Source", 2));
                if (source.getSourceType() != null) {
                    tb.row("Type", source.getSourceType().getDisplayValue());
                }
                if (source.getOwnerDocRef() != null) {
                    tb.row("Owner Doc", source.getOwnerDocRef().toInfoString());
                }
                if (source.getComponentId() != null) {
                    tb.row("Component Id", source.getComponentId());
                }
            }

            if (resultStoreInfo.getSearchProcessLifespan() != null) {
                tb.row(TableCell.header("Search Process Lifespan", 2));
                addLifespan(tb, resultStoreInfo.getSearchProcessLifespan());
            }
            if (resultStoreInfo.getStoreLifespan() != null) {
                tb.row(TableCell.header("Store Lifespan", 2));
                addLifespan(tb, resultStoreInfo.getStoreLifespan());
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("info-table"));
        view.setDetails(htmlBuilder.toSafeHtml());
    }

    private void addLifespan(final TableBuilder tb, final LifespanInfo lifespan) {
        tb
                .row("Time To Live", lifespan.getTimeToLive())
                .row("Time To Idle", lifespan.getTimeToIdle())
                .row("Destroy On Tab Close", Boolean.toString(lifespan.isDestroyOnTabClose()))
                .row("Destroy On Window Close", Boolean.toString(lifespan.isDestroyOnWindowClose()));
    }
}
