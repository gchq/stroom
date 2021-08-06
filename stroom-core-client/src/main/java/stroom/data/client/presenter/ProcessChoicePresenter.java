package stroom.data.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ProcessChoicePresenter extends MyPresenterWidget<ProcessChoicePresenter.ProcessChoiceView> {

    @Inject
    public ProcessChoicePresenter(final EventBus eventBus,
                                  final ProcessChoiceView view) {
        super(eventBus, view);
    }

    public void show(final ProcessChoiceUiHandler processorChoiceUiHandler) {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Create Processors")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ProcessChoice processChoice = new ProcessChoice(
                                getView().getPriority(),
                                getView().isAutoPriority(),
                                getView().isReprocess(),
                                getView().isEnabled());
                        processorChoiceUiHandler.onChoice(processChoice);
                    }
                    e.hide();
                })
                .fire();
    }

    public interface ProcessChoiceView extends View, Focus {

        int getPriority();

        boolean isAutoPriority();

        boolean isReprocess();

        boolean isEnabled();
    }
}
