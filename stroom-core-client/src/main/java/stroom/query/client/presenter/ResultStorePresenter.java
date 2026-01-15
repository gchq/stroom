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

package stroom.query.client.presenter;

import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.LifespanInfo;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchTaskProgress;
import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ResultStorePresenter extends MyPresenterWidget<ResultStoreView> {

    private final ResultStoreListPresenter resultStoreListPresenter;
    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public ResultStorePresenter(final EventBus eventBus,
                                final ResultStoreView view,
                                final ResultStoreListPresenter resultStoreListPresenter,
                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.resultStoreListPresenter = resultStoreListPresenter;
        this.dateTimeFormatter = dateTimeFormatter;

        view.setListView(resultStoreListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(resultStoreListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final TableBuilder tb = new TableBuilder();
            final ResultStoreInfo resultStoreInfo = resultStoreListPresenter.getSelectionModel().getSelected();
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
            htmlBuilder.div(tb::write, Attribute.className("infoTable"));
            getView().setData(htmlBuilder.toSafeHtml());
        }));
    }

    private void addLifespan(final TableBuilder tb, final LifespanInfo lifespan) {
        tb
                .row("Time To Live", lifespan.getTimeToLive())
                .row("Time To Idle", lifespan.getTimeToIdle())
                .row("Destroy On Tab Close", Boolean.toString(lifespan.isDestroyOnTabClose()))
                .row("Destroy On Window Close", Boolean.toString(lifespan.isDestroyOnWindowClose()));
    }

    public void show() {
        resultStoreListPresenter.refresh();

        final PopupSize popupSize = PopupSize.resizable(1000, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Search Result Stores")
                .fire();
    }

    public interface ResultStoreView extends View {

        void setListView(View view);

        void setData(SafeHtml safeHtml);
    }
}
