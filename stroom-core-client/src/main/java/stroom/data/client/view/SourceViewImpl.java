package stroom.data.client.view;

import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.pager.client.DataNavigator;
import stroom.editor.client.presenter.EditorView;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.client.SvgPreset;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceViewImpl extends ViewImpl implements SourceView {

    private SourceLocation sourceLocation;
    private Widget widget;

    @UiField
    Label lblId;
    @UiField
    Label lblType;
    @UiField
    ResizeSimplePanel container;
    @UiField
    DataNavigator dataNavigator;
    @UiField
    ButtonPanel buttonPanel;

    @Inject
    public SourceViewImpl(final EventBus eventBus,
                          final Binder binder) {
        widget = binder.createAndBindUi(this);

        dataNavigator.setVisible(false);
    }

   @Override
    public void addToSlot(final Object slot, final Widget content) {

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {

    }

//    @Override
//    public void setSourceLocation(final SourceLocation sourceLocation) {
//        this.sourceLocation = sourceLocation;
//    }


    @Override
    public void setTitle(final String id, final String type) {
        lblId.setText(id);
        lblType.setText(type);
    }

    @Override
    public void setEditorView(final EditorView editorView) {
        this.container.setWidget(editorView.asWidget());
    }

    @Override
    public ButtonView addButton(final SvgPreset preset) {
        return buttonPanel.addButton(preset);
    }

    public interface Binder extends UiBinder<Widget, SourceViewImpl> {
    }
}
