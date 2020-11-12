package stroom.data.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.SourceKey;
import stroom.data.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class SourceTabPresenter extends ContentTabPresenter<SourceTabView> {

    private final ClassificationWrappedSourcePresenter sourcePresenter;
//    private SourceLocation sourceLocation = null;
    private SourceKey sourceKey;

    @Inject
    public SourceTabPresenter(final EventBus eventBus,
                              final ClassificationWrappedSourcePresenter sourcePresenter,
                              final SourceTabView view) {
        super(eventBus, view);
        this.sourcePresenter = sourcePresenter;

        getView().setSourceView(sourcePresenter.getView());
    }

    @Override
    protected void onBind() {
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.RAW;
    }

    @Override
    public String getLabel() {
        final String type = sourceKey.getOptChildStreamType()
                .map(val -> " (" + val + ")")
                .orElse("");
        return sourceKey != null
                ? ("Stream " + sourceKey.getMetaId()
                    + ":" + (sourceKey.getPartNo() + 1) // to one based
                    + ":" + (sourceKey.getSegmentNo().orElse(0L) + 1) // to one based
                    + type)
                : "Source Data";
    }
    public void setSourceLocationUsingHighlight(final SourceLocation sourceLocation) {
        sourcePresenter.setSourceLocationUsingHighlight(sourceLocation);
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
//        this.sourceLocation = sourceLocation;
        sourcePresenter.setSourceLocation(sourceLocation);
    }

    public void setSourceKey(final SourceKey sourceKey) {
        this.sourceKey = sourceKey;
//        sourcePresenter.setSourceKey(sourceKey);

        sourcePresenter.setSourceLocation(SourceLocation.builder(sourceKey.getMetaId())
                .withChildStreamType(sourceKey.getOptChildStreamType().orElse(null))
                .build());
    }

    public interface SourceTabView extends View {
        void setSourceView(final ClassificationWrapperView sourceView);
    }
}
