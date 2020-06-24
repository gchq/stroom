package stroom.data.client.presenter;

import stroom.alert.client.presenter.CommonAlertPresenter.CommonAlertView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.SelectionSummary;
import stroom.util.client.SafeHtmlUtil;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

public class SelectionSummaryPresenter extends MyPresenterWidget<CommonAlertView> {
    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;

    @Inject
    public SelectionSummaryPresenter(final EventBus eventBus,
                                     final CommonAlertView view,
                                     final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void show(final FindMetaCriteria criteria, final String postAction, final String action, final String caption, final boolean reprocess, final Runnable runnable) {
        getView().setInfo(SafeHtmlUtil.getSafeHtml("Fetching selection summary. Please wait..."));

        final Rest<SelectionSummary> rest = restFactory.create();
        if (reprocess) {
            rest
                    .onSuccess(result -> update(postAction, action, result))
                    .call(META_RESOURCE)
                    .getReprocessSelectionSummary(criteria);
        } else {
            rest
                    .onSuccess(result -> update(postAction, action, result))
                    .call(META_RESOURCE)
                    .getSelectionSummary(criteria);
        }

        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, caption, new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    runnable.run();
                }
                HidePopupEvent.fire(SelectionSummaryPresenter.this, SelectionSummaryPresenter.this, autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        });
    }

    private void update(final String postAction, final String action, final SelectionSummary result) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        appendRow(sb, "item", "items", result.getItemCount());
        sb.appendEscaped(" will be ");
        sb.appendEscaped(postAction);
        sb.appendHtmlConstant("</br>");
        sb.appendHtmlConstant("</br>");
        sb.appendEscaped("The selected items include:");
        sb.appendHtmlConstant("</br>");
        appendRow(sb, "type", "types", result.getTypeCount());
        sb.appendHtmlConstant(" of ");
        appendRow(sb, "status", "statuses", result.getStatusCount());
        sb.appendHtmlConstant("</br>");
        sb.appendHtmlConstant("</br>");
        sb.appendEscaped("That are associated with:");
        sb.appendHtmlConstant("</br>");
        appendRow(sb, "pipeline", "pipelines", result.getPipelineCount());
        sb.appendHtmlConstant("</br>");
        appendRow(sb, "feed", "feeds", result.getFeedCount());
        sb.appendHtmlConstant("</br>");
        sb.appendHtmlConstant("</br>");
        if (result.getAgeRange().getFrom() != null || result.getAgeRange().getTo() != null) {
            sb.appendEscaped("Created Between: ");
            sb.appendHtmlConstant("</br>");
            sb.appendEscaped(ClientDateUtil.toISOString(result.getAgeRange().getFrom()));
            sb.appendHtmlConstant("</br>");
            sb.appendEscaped(ClientDateUtil.toISOString(result.getAgeRange().getTo()));
        } else {
            sb.appendEscaped("Created at any time.");
        }
        sb.appendHtmlConstant("</br>");
        sb.appendHtmlConstant("</br>");
        sb.appendEscaped("Are you sure you want to ");
        sb.appendEscaped(action);
        sb.appendEscaped("?");

        getView().setQuestion(sb.toSafeHtml());
    }

    private void appendRow(final SafeHtmlBuilder sb, final String type, final String pluralType, final long count) {
        sb.appendHtmlConstant("<b>");
        sb.append(count);
        sb.appendHtmlConstant("</b>");
        sb.appendEscaped(" ");

        if (count == 0 || count > 1) {
            sb.appendEscaped(pluralType);
        } else if (count > 0) {
            sb.appendEscaped(type);
        }
    }
}
