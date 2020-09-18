package stroom.data.client.presenter;

import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.pipeline.shared.SourceLocation;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class SourcePresenter extends MyPresenterWidget<SourceView> {

    private final TextPresenter textPresenter;

    @Inject
    public SourcePresenter(final EventBus eventBus,
                           final SourceView view,
                           final TextPresenter textPresenter) {
        super(eventBus, view);
        this.textPresenter = textPresenter;
    }

    @Override
    protected void onBind() {
        getView().setTextView(textPresenter.getView());
    }

    public interface SourceView extends View {

        void setSourceLocation(final SourceLocation sourceLocation);

        void setTextView(final TextView textView);
    }

}
