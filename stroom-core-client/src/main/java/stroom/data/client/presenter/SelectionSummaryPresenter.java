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

package stroom.data.client.presenter;

import stroom.alert.client.presenter.CommonAlertPresenter.CommonAlertView;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SelectionSummaryRequest;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.DocumentPermission;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Set;

public class SelectionSummaryPresenter
        extends MyPresenterWidget<CommonAlertView> {

    public static final int MAX_DISTINCT_ITEMS = Math.min(10, SelectionSummary.MAX_GROUP_CONCAT_PARTS);
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public SelectionSummaryPresenter(final EventBus eventBus,
                                     final CommonAlertView view,
                                     final RestFactory restFactory,
                                     final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public void show(final FindMetaCriteria criteria,
                     final DocumentPermission permission,
                     final String postAction,
                     final String action,
                     final String caption,
                     final boolean reprocess,
                     final Runnable runnable) {

        getView().setInfo(SafeHtmlUtil.getSafeHtml("Fetching selection summary. Please wait..."));

        final PopupType popupType = postAction != null
                ? PopupType.OK_CANCEL_DIALOG
                : PopupType.CLOSE_DIALOG;
        ShowPopupEvent.builder(this)
                .popupType(popupType)
                .caption(caption)
                .onShow(e -> {
                    if (reprocess) {
                        restFactory
                                .create(META_RESOURCE)
                                .method(res -> res.getReprocessSelectionSummary(
                                        new SelectionSummaryRequest(criteria, permission)))
                                .onSuccess(result ->
                                        update(postAction, action, result))
                                .taskMonitorFactory(this)
                                .exec();
                    } else {
                        restFactory
                                .create(META_RESOURCE)
                                .method(res ->
                                        res.getSelectionSummary(new SelectionSummaryRequest(criteria, permission)))
                                .onSuccess(result ->
                                        update(postAction, action, result))
                                .taskMonitorFactory(this)
                                .exec();
                    }
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        runnable.run();
                    }
                    e.hide();
                })
                .fire();
    }

    private void update(final String postAction, final String action, final SelectionSummary result) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        appendRow(sb, "item", "items", result.getItemCount());
        if (postAction != null) {
            sb.appendEscaped(" will be ");
            sb.appendEscaped(postAction);
            sb.appendEscaped(". ");
        } else {
            sb.appendEscaped(" selected. ");
        }
        sb.appendEscaped("The selected items include:");
        sb.appendHtmlConstant("</br>");
        sb.appendHtmlConstant("</br>");
        appendCountWithValues(
                sb, "type", "types", result.getTypeCount(), result.getDistinctTypes(), 1);
        appendCountWithValues(
                sb,
                "status",
                "statuses",
                result.getStatusCount(),
                result.getDistinctStatuses(),
                2);
        sb.appendEscaped("That are associated with:");
        sb.appendHtmlConstant("</br>");
        appendRow(sb, "pipeline", "pipelines", result.getPipelineCount());
        sb.appendHtmlConstant("</br>");
        appendCountWithValues(sb,
                "feed",
                "feeds",
                result.getFeedCount(),
                result.getDistinctFeeds(),
                2);
        if (result.getAgeRange().getFrom() != null || result.getAgeRange().getTo() != null) {
            sb.appendEscaped("Created Between: ");
            sb.appendEscaped(dateTimeFormatter.format(result.getAgeRange().getFrom()));
            sb.appendHtmlConstant(" and ");
            sb.appendEscaped(dateTimeFormatter.format(result.getAgeRange().getTo()));
        } else {
            sb.appendEscaped("Created at any time.");
        }
        if (action != null) {
            sb.appendHtmlConstant("</br>");
            sb.appendHtmlConstant("</br>");
            sb.appendHtmlConstant("<b>");
            sb.appendEscaped("Unless the database is modified in the meantime.");
            sb.appendHtmlConstant("</b>");
            sb.appendHtmlConstant("</br>");
            sb.appendHtmlConstant("</br>");
            sb.appendEscaped("Are you sure you want to ");
            sb.appendEscaped(action);
            sb.appendEscaped("?");
            getView().setQuestion(sb.toSafeHtml());
        } else {
            getView().setInfo(sb.toSafeHtml());
        }
    }

    private void appendRow(final SafeHtmlBuilder sb, final String type, final String pluralType, final long count) {
        sb.appendHtmlConstant("<b>");
        sb.appendEscaped(NumberFormat.getDecimalFormat().format(count));
        sb.appendHtmlConstant("</b>");
        sb.appendEscaped(" ");

        if (count == 0 || count > 1) {
            sb.appendEscaped(pluralType);
        } else if (count > 0) {
            sb.appendEscaped(type);
        }
    }

    private void appendCountWithValues(final SafeHtmlBuilder sb,
                                       final String type,
                                       final String pluralType,
                                       final long count,
                                       final Set<String> values) {
        appendCountWithValues(sb, type, pluralType, count, values, 0);
    }


    private void appendCountWithValues(final SafeHtmlBuilder sb,
                                       final String type,
                                       final String pluralType,
                                       final long count,
                                       final Set<String> values,
                                       final int lineBreakCount) {
        sb.appendHtmlConstant("<b>");
        sb.appendEscaped(NumberFormat.getDecimalFormat().format(count));
        sb.appendHtmlConstant("</b>");
        sb.appendEscaped(" ");

        if (count == 0 || count > 1) {
            sb.appendEscaped(pluralType);
        } else if (count > 0) {
            sb.appendEscaped(type);
        }

        if (count == 1) {
            sb.appendEscaped(": ")
                    .appendEscaped(values.iterator().next());
        } else if (count > 1) {
            if (count > values.size()) {
                sb.appendEscaped(": (showing first ")
                        .appendEscaped(String.valueOf(MAX_DISTINCT_ITEMS))
                        .appendEscaped(")");
            } else {
                sb.appendEscaped(":");
            }

            sb.appendHtmlConstant("<ul>");
            values.stream()
                    .sorted()
                    .forEach(val -> {
                        sb.appendHtmlConstant("<li>")
                                .appendEscaped(val)
                                .appendHtmlConstant("</li>");
                    });
            sb.appendHtmlConstant("</ul>");
        }

        // <ul> gets styled with a bottom margin
        if (count <= 1) {
            for (int i = 0; i < lineBreakCount; i++) {
                sb.appendHtmlConstant("<br/>");
            }
        }
    }
}
