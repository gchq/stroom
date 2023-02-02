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

package stroom.query.client.presenter;

import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.v2.LifespanInfo;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tooltip.client.presenter.TableBuilder;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
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
                tb.row().header("Store Details", 2);
                tb.row()
                        .data("UUID:")
                        .data(resultStoreInfo.getQueryKey().getUuid());
                tb.row()
                        .data("User Id:")
                        .data(resultStoreInfo.getUserId());
                tb.row()
                        .data("Creation Time:")
                        .data(dateTimeFormatter.format(resultStoreInfo.getCreationTime()));
                tb.row()
                        .data("Age:")
                        .data(ModelStringUtil.formatDurationString(
                                System.currentTimeMillis() - resultStoreInfo.getCreationTime()));
                tb.row()
                        .data("Node Name:")
                        .data(resultStoreInfo.getNodeName());
                tb.row()
                        .data("Store Size:")
                        .data(ModelStringUtil.formatIECByteSizeString(resultStoreInfo.getStoreSize()));
                tb.row()
                        .data("Complete:")
                        .data(Boolean.toString(resultStoreInfo.isComplete()));
                final SearchTaskProgress taskProgress = resultStoreInfo.getTaskProgress();
                if (taskProgress != null) {
                    tb.row()
                            .data("Task Info:")
                            .data(taskProgress.getTaskInfo());
                }

                tb.row().header("Search Process Lifespan", 2);
                addLifespan(tb, resultStoreInfo.getSearchProcessLifespan());
                tb.row().header("Store Lifespan", 2);
                addLifespan(tb, resultStoreInfo.getStoreLifespan());
            }

            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            sb.appendHtmlConstant("<div class=\"resultStoreInfo\">");
            tb.write(sb);
            sb.appendHtmlConstant("</div>");

            getView().setData(sb.toSafeHtml());
        }));
    }

    private void addLifespan(final TableBuilder tb, final LifespanInfo lifespan) {
        tb.row()
                .data("Time To Live:")
                .data(lifespan.getTimeToLive());
        tb.row()
                .data("Time To Idle:")
                .data(lifespan.getTimeToIdle());
        tb.row()
                .data("Destroy On Tab Close:")
                .data(Boolean.toString(lifespan.isDestroyOnTabClose()));
        tb.row()
                .data("Destroy On Window Close:")
                .data(Boolean.toString(lifespan.isDestroyOnWindowClose()));
    }

    public void show() {
        resultStoreListPresenter.refresh();

        final PopupSize popupSize = PopupSize.resizable(1000, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Search Result Stores")
//                .onShow(e -> getView().focus())
//                .onHide(e -> {
//                    if (e.isOk() && groupConsumer != null) {
//                        final User selected = getSelectionModel().getSelected();
//                        if (selected != null) {
//                            groupConsumer.accept(selected);
//                        }
//                    }
//                })
                .fire();
    }

    public interface ResultStoreView extends View {

        void setListView(View view);

        void setData(SafeHtml safeHtml);
    }
}
