package stroom.data.client.presenter;

import stroom.data.client.SourceKey;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.ButtonView;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class SourcePresenter extends MyPresenterWidget<SourceView> {

    private final EditorPresenter editorPresenter;
    private SourceLocation sourceLocation = null;
    private SourceKey sourceKey;

    @Inject
    public SourcePresenter(final EventBus eventBus,
                           final SourceView view,
                           final EditorPresenter editorPresenter) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;

        editorPresenter.setText("hello");

        getView().setEditorView(editorPresenter.getView());
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
        getView().setSourceLocation(sourceLocation);
    }

    public void setSourceKey(final SourceKey sourceKey) {
        this.sourceKey = sourceKey;
    }

    @Override
    protected void onBind() {

    }

    public interface SourceView extends View {

        void setSourceLocation(final SourceLocation sourceLocation);

        void setEditorView(final EditorView editorView);

        ButtonView addButton(final SvgPreset preset);
    }
}
