package stroom.query.client.view;

import stroom.query.client.presenter.QueryDataUiHandlers;
import stroom.query.client.presenter.QueryDataView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.presenter.client.View;
import stroom.widget.presenter.client.ViewWithUiHandlers;

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
    Toolbar toolbar;
    @UiField
    SimplePanel tableContainer;

    @Inject
    public QueryDataViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        toolbar.button(SvgImage.UNDO, "Reset to Default", this::onUndo)
                .addStyleName("query-buttons__button");
        toolbar.button(SvgImage.PLAY, "Run", this::onRun)
                .addStyleName("query-buttons__button query-buttons__button--play");
        toolbar.button(SvgImage.STOP, "Stop", this::onStop)
                .addStyleName("query-buttons__button query-buttons__button--stop");
        toolbar.button(SvgImage.DOCUMENT_DASHBOARD, "Create Dashboard", this::onCreateDashboard)
                .addStyleName("query-buttons__button");
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
            // Shown, and told to screen readers (aria-invalid)
            FieldValidity.setInvalid(query.getElement(), true);
        } else {
            clearError();
        }
    }

    @Override
    public void clearError() {
        errorLabel.setText("");
        errorContainer.setVisible(false);
        FieldValidity.setInvalid(query.getElement(), false);
    }

    @Override
    public void selectQueryRange(final int pos, final int length) {
        query.setFocus(true);
        query.setSelectionRange(pos, length);
    }

    private void onUndo() {
        if (getUiHandlers() != null) {
            getUiHandlers().onReset();
        }
    }

    private void onRun() {
        if (getUiHandlers() != null) {
            getUiHandlers().onRun();
        }
    }

    private void onStop() {
        if (getUiHandlers() != null) {
            getUiHandlers().onStop();
        }
    }

    private void onCreateDashboard() {
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
