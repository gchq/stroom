package stroom.explorer.client.presenter;

import stroom.explorer.client.presenter.TypeFilterPresenter.TypeFilterView;

import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class TypeFilterViewImpl extends ViewImpl implements TypeFilterView {
    private final ScrollPanel scrollPanel;

    public TypeFilterViewImpl() {
        scrollPanel = new ScrollPanel();
        scrollPanel.getElement().getStyle().setProperty("minWidth", 50 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxWidth", 600 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxHeight", 600 + "px");
    }

    @Override
    public void setWidget(final Widget widget) {
        scrollPanel.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return scrollPanel;
    }
}
