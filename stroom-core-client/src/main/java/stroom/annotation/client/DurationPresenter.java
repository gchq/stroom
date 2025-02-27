package stroom.annotation.client;

import stroom.annotation.client.DurationPresenter.DurationView;
import stroom.util.shared.time.SimpleDuration;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class DurationPresenter extends MyPresenterWidget<DurationView> {

    @Inject
    public DurationPresenter(final EventBus eventBus, final DurationView view) {
        super(eventBus, view);
    }

    public void setDuration(final SimpleDuration duration) {
        getView().setDuration(duration);
    }

    public SimpleDuration getDuration() {
        return getView().getDuration();
    }

    public interface DurationView extends View, Focus {

        void setDuration(SimpleDuration duration);

        SimpleDuration getDuration();
    }
}
