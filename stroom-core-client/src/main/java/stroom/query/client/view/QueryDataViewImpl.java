package stroom.query.client.view;

import stroom.query.client.presenter.QueryDataUiHandlers;
import stroom.query.client.presenter.QueryDataView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class QueryDataViewImpl extends ViewWithUiHandlers<QueryDataUiHandlers> implements QueryDataView {

    public interface Binder extends UiBinder<Widget, QueryDataViewImpl> {
    }

    private final Widget widget;

    @UiField
    TextBox query;
    @UiField
    ScrollPanel errorContainer;
    @UiField
    Label errorLabel;
    @UiField
    InlineSvgButton undo;
    @UiField
    InlineSvgButton run;
    @UiField
    InlineSvgButton stop;
    @UiField
    InlineSvgButton createDashboard;
    @UiField
    SimplePanel tableContainer;

    @Inject
    public QueryDataViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        undo.setSvg(SvgImage.UNDO);
        run.setSvg(SvgImage.PLAY);
        stop.setSvg(SvgImage.STOP);
        createDashboard.setSvg(SvgImage.DOCUMENT_DASHBOARD);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setQuery(final String queryText) {
        query.setText(queryText);
    }

    @Override
    public String getQuery() {
        return query.getText();
    }

    @Override
    public void setTable(final View view) {
        view.asWidget().addStyleName("TablePresenter");
        tableContainer.setWidget(view.asWidget());
    }

    @Override
    public void setError(final String error) {
        if (error != null && !error.trim().isEmpty()) {
            errorLabel.setText(error);
            errorContainer.setVisible(true);
            query.addStyleName("invalid");
        } else {
            clearError();
        }
    }

    @Override
    public void clearError() {
        errorLabel.setText("");
        errorContainer.setVisible(false);
        query.removeStyleName("invalid");
    }

    @Override
    public void selectQueryRange(final int pos, final int length) {
        query.setFocus(true);
        query.setSelectionRange(pos, length);
    }

    @UiHandler("undo")
    @SuppressWarnings("unused")
    public void onUndo(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onReset();
        }
    }

    @UiHandler("run")
    @SuppressWarnings("unused")
    public void onRun(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRun();
        }
    }

    @UiHandler("stop")
    @SuppressWarnings("unused")
    public void onStop(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onStop();
        }
    }

    @UiHandler("createDashboard")
    @SuppressWarnings("unused")
    public void onCreateDashboard(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onCreateDashboard();
        }
    }

    @UiHandler("query")
    @SuppressWarnings("unused")
    public void onQueryKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            if (getUiHandlers() != null) {
                getUiHandlers().onRun();
            }
        }
    }
}
