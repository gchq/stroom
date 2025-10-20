package stroom.dashboard.client.table;

import stroom.dashboard.client.table.ColumnValuesFilterPresenter.ColumnValuesFilterView;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;

public class ColumnValuesFilterViewImpl
        extends ViewWithUiHandlers<ColumnValuesFilterUiHandlers>
        implements ColumnValuesFilterView {

    private final Widget widget;

    @UiField
    Label selectAllLink;
    @UiField
    Label selectNoneLink;
    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel data;

    @Inject
    public ColumnValuesFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
    }

    @Override
    public void focus() {
        quickFilter.forceFocus();
    }

    @Override
    public void setDataView(final View view) {
        data.setWidget(view.asWidget());
    }

    @Override
    public void setText(final String text, final boolean fireEvents) {
        quickFilter.setText(text, fireEvents);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler(value = "selectAllLink")
    void onSelectAllLink(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSelectAll();
        }
    }

    @UiHandler(value = "selectNoneLink")
    void onSelectNoneLink(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSelectNone();
        }
    }

    @UiHandler("quickFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().onFilterChange(quickFilter.getText());
    }

    public interface Binder extends UiBinder<Widget, ColumnValuesFilterViewImpl> {

    }
}
