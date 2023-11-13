package stroom.item.client;

import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.view.SimplePopupLayout;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

public class SelectionPopup extends Composite {

    private final PopupPanel popupPanel;
    private final SelectionList selectionList;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            super.onBind();
            registerHandler(popupPanel.addCloseHandler(event -> hide()));
        }
    };

    public SelectionPopup() {
        popupPanel = new PopupPanel();
        selectionList = new SelectionList();

        final SimplePopupLayout simplePopupLayout = new SimplePopupLayout();
        simplePopupLayout.setContent(selectionList);

        popupPanel.add(simplePopupLayout);
        popupPanel.setAutoHideEnabled(true);
        popupPanel.setStyleName("SelectionPopup");
    }

    public void addAutoHidePartner(Element partner) {
        popupPanel.addAutoHidePartner(partner);
    }

    public void setModel(final SelectionListModel model) {
        selectionList.setModel(model);
    }

    public MultiSelectionModel<SelectionItem> getSelectionModel() {
        return selectionList.getSelectionModel();
    }

    public void setEnabled(final boolean enabled) {
        if (!enabled) {
            popupPanel.hide();
        }
    }

    public void show(final Widget displayTarget) {
        final PopupPosition position = new PopupPosition(displayTarget.getAbsoluteLeft() - 4,
                displayTarget.getAbsoluteTop() + displayTarget.getOffsetHeight() + 4);
        show(position);
    }

    public void show(final PopupPosition position) {
        if (popupPanel.isShowing()) {
            hide();
        } else {
            selectionList.reset();

            eventBinder.bind();
            popupPanel.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {
                popupPanel.setPopupPosition(
                        (int) position.getRelativeRect().getLeft(),
                        (int) position.getRelativeRect().getTop());
                afterShow();
            });
        }
    }

    private void afterShow() {
        Scheduler.get().scheduleDeferred(selectionList::focus);
    }

    public void hide() {
        eventBinder.unbind();
        popupPanel.hide();
    }

    public HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler) {
        return popupPanel.addCloseHandler(handler);
    }
}
