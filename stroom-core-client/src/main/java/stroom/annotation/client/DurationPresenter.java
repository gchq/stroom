package stroom.annotation.client;

import stroom.annotation.client.DurationPresenter.DurationView;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class DurationPresenter extends MyPresenterWidget<DurationView> {

    @Inject
    public DurationPresenter(final EventBus eventBus, final DurationView view) {
        super(eventBus, view);
    }

    public void show(final SimpleDuration duration,
                     final Consumer<SimpleDuration> consumer) {
        getView().setDuration(duration);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Set Retention Period")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    e.hide();
                    if (e.isOk()) {
                        consumer.accept(getView().getDuration());
                    }
                })
                .fire();
    }

    public interface DurationView extends View, Focus {

        void setDuration(SimpleDuration duration);

        SimpleDuration getDuration();
    }
}
