package stroom.data.client.view;

import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceTabViewImpl extends ViewImpl implements SourceTabView {

    private final Widget widget;

    @UiField
    SimplePanel container;

    @Inject
    public SourceTabViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    @Override
    public void setSourceView(final View sourceView) {
        container.setWidget(sourceView.asWidget());
    }

    public interface Binder extends UiBinder<Widget, SourceTabViewImpl> {

    }
}
