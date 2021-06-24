package stroom.data.client.view;

import stroom.data.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.data.client.presenter.DataPresenter;
import stroom.data.client.presenter.DataPreviewTabPresenter.DataPreviewTabView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class DataPreviewTabViewImpl extends ViewImpl implements DataPreviewTabView {

    private final DataPresenter dataPresenter;

    private final Widget widget;

    @UiField
    SimplePanel container;

    @Inject
    public DataPreviewTabViewImpl(final EventBus eventBus,
                                  final DataPresenter dataPresenter,
                                  final Binder binder) {
        this.dataPresenter = dataPresenter;
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void addToSlot(final Object slot, final Widget content) {

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setContentView(final ClassificationWrapperView view) {
        container.setWidget(view.asWidget());
    }

    public interface Binder extends UiBinder<Widget, DataPreviewTabViewImpl> {

    }
}
