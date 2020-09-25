package stroom.data.client.view;

import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
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
    Label lblFeed;
    @UiField
    Label lblId;
    @UiField
    Label lblPartNo;
    @UiField
    Label lblSegmentNo;
    @UiField
    Label lblType;
    @UiField
    ResizeSimplePanel container;
    @UiField
    ResizeSimplePanel navigatorContainer;
//    @UiField
//    CharacterNavigatorViewImpl characterNavigator;
    @UiField
    ButtonPanel buttonPanel;

    @Inject
    public SourceViewImpl(final EventBus eventBus,
                          final Binder binder) {
        widget = binder.createAndBindUi(this);

//        characterNavigator.setVisible(true);
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
    public void setTitle(final String feedName,
                         final long id,
                         final long partNo,
                         final long segmentNo,
                         final String type) {
        lblFeed.setText(feedName);
        lblId.setText(Long.toString(id));
        lblPartNo.setText(Long.toString(partNo + 1));
        lblSegmentNo.setText(Long.toString(segmentNo + 1));
        lblType.setText(type);
    }

//    @Override
//    public void setNavigatorClickHandler(final Runnable clickHandler) {
//        characterNavigator.setClickHandler(clickHandler);
//    }

//    @Override
//    public void setNavigatorData(final HasCharacterData dataNavigatorData) {
//        characterNavigator.setDisplay(dataNavigatorData);
//    }

//    @Override
//    public void refreshNavigator() {
//        characterNavigator.refresh();
//    }

    @Override
    public void setTextView(final TextView textView) {
        container.setWidget(textView.asWidget());
    }

    @Override
    public void setNavigatorView(final CharacterNavigatorView characterNavigatorView) {
        navigatorContainer.setWidget(characterNavigatorView.asWidget());
    }

    @Override
    public ButtonView addButton(final SvgPreset preset) {
        return buttonPanel.addButton(preset);
    }

    public interface Binder extends UiBinder<Widget, SourceViewImpl> {
    }
}
