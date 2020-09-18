package stroom.data.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.SourceKey;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class SourceTabPresenter extends ContentTabPresenter<SourceTabView> {

    private final SourcePresenter sourcePresenter;
    private SourceLocation sourceLocation = null;
    private SourceKey sourceKey;

    @Inject
    public SourceTabPresenter(final EventBus eventBus,
                              final SourcePresenter sourcePresenter,
                              final SourceTabView view) {
        super(eventBus, view);
        this.sourcePresenter = sourcePresenter;
    }

    @Override
    protected void onBind() {
        getView().setSourceView(sourcePresenter.getView());
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.RAW;
    }

    @Override
    public String getLabel() {
        String type = sourceKey.getStreamType() != null
                ? sourceKey.getStreamType()
                : "Data";
        return sourceKey != null
        ? ("ID: " + sourceKey.getMetaId() + " (" + type + ")")
        : "Source Data";
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public void setSourceKey(final SourceKey sourceKey) {
        this.sourceKey = sourceKey;
    }

    public interface SourceTabView extends View {
        void setSourceView(final SourceView sourceView);
    }
}
