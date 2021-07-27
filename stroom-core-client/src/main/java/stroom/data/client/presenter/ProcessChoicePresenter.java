package stroom.data.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

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
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, "Create Processors",
                new DefaultPopupUiHandlers(this) {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            final ProcessChoice processChoice = new ProcessChoice(
                                    getView().getPriority(),
                                    getView().isAutoPriority(),
                                    getView().isReprocess(),
                                    getView().isEnabled());
                            processorChoiceUiHandler.onChoice(processChoice);
                        }
                        hide(autoClose, ok);
                    }
                });
    }

    public interface ProcessChoiceView extends View {

        int getPriority();

        boolean isAutoPriority();

        boolean isReprocess();

        boolean isEnabled();
    }
}
