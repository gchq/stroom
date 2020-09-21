package stroom.data.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.SourceKey;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.data.shared.StreamTypeNames;
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
        final String type = sourceKey.getChildStreamType() != null
                ? ("(" + StreamTypeNames.asUiName(sourceKey.getChildStreamType()) + ")")
                : "";
        return sourceKey != null
                ? ("ID: " + sourceKey.getMetaId() + " " + type)
                : "Source Data";
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
        sourcePresenter.setSourceLocation(sourceLocation);
    }

    public void setSourceKey(final SourceKey sourceKey) {
        this.sourceKey = sourceKey;
//        sourcePresenter.setSourceKey(sourceKey);

        sourcePresenter.setSourceLocation(SourceLocation.builder(sourceKey.getMetaId())
                .withChildStreamType(sourceKey.getChildStreamType())
                .build());
    }

    public interface SourceTabView extends View {
        void setSourceView(final SourceView sourceView);
    }





}
