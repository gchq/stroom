package stroom.dashboard.client.table;

import stroom.dashboard.client.table.ColumnValuesFilterPresenter.ColumnValuesFilterView;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ColumnValuesFilterViewImpl
        extends ViewWithUiHandlers<ColumnValuesFilterUiHandlers>
        implements ColumnValuesFilterView {

    private final Widget widget;

    @UiField
    Label selectAllLink;
    @UiField
    Label selectNoneLink;
    @UiField
    SimplePanel listContainer;

    @Inject
    public ColumnValuesFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setList(final View view) {
        listContainer.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler(value = "selectAllLink")
    void onSelectAllLink(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSelectAll();
        }
    }

    @UiHandler(value = "selectNoneLink")
    void onSelectNoneLink(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSelectNone();
        }
    }

    public interface Binder extends UiBinder<Widget, ColumnValuesFilterViewImpl> {

    }
}
