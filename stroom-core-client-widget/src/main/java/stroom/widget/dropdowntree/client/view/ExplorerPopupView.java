package stroom.widget.dropdowntree.client.view;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

public interface ExplorerPopupView extends View, Focus, HasUiHandlers<ExplorerPopupUiHandlers> {

    void setCellTree(Widget widget);

    void setQuickFilterTooltipSupplier(final Supplier<SafeHtml> tooltipSupplier);

    void clearQuickFilter();

    void setQuickFilter(final String filterInput);
}
