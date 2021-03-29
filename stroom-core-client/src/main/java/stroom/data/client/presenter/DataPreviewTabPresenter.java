package stroom.data.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.DataPreviewKey;
import stroom.data.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.data.client.presenter.DataPreviewTabPresenter.DataPreviewTabView;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class DataPreviewTabPresenter extends ContentTabPresenter<DataPreviewTabView> {

    private final ClassificationWrappedDataPresenter dataPresenter;
    private DataPreviewKey dataPreviewKey;

    @Inject
    public DataPreviewTabPresenter(final EventBus eventBus,
                                   final ClassificationWrappedDataPresenter dataPresenter,
                                   final DataPreviewTabView view) {
        super(eventBus, view);
        this.dataPresenter = dataPresenter;
        this.dataPresenter.setDisplayMode(DisplayMode.STROOM_TAB);
        getView().setContentView(dataPresenter.getView());
    }

    @Override
    protected void onBind() {
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.FILE_FORMATTED;
    }

    @Override
    public String getLabel() {
        return dataPreviewKey != null
                ? "Stream " + dataPreviewKey.getMetaId()
                : "Data Preview";
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        dataPreviewKey = new DataPreviewKey(sourceLocation);
        dataPresenter.fetchData(sourceLocation);
    }

    public interface DataPreviewTabView extends View {

        void setContentView(final ClassificationWrapperView sourceView);
    }
}
