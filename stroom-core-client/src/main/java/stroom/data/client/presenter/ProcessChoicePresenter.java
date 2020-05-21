package stroom.data.client.presenter;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
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
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, "Create Processors", new PopupUiHandlers() {
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
                hide();
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        });
    }

    private void hide() {
        HidePopupEvent.fire(ProcessChoicePresenter.this, ProcessChoicePresenter.this, false, true);
    }

    public interface ProcessChoiceView extends View {
        int getPriority();

        boolean isAutoPriority();

        boolean isReprocess();

        boolean isEnabled();
    }
}
