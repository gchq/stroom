package stroom.widget.help.client.presenter;

import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.AbstractPopupPanel;
import stroom.widget.popup.client.view.Popup;
import stroom.widget.popup.client.view.PopupUtil;
import stroom.widget.tooltip.client.event.ShowHelpEvent;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class HelpManager {

    @Inject
    public HelpManager(final EventBus eventBus) {
        eventBus.addHandler(ShowHelpEvent.getType(), this::onShow);
    }

    private void onShow(final ShowHelpEvent showHelpEvent) {

        final HelpPopup popup = new HelpPopup(showHelpEvent.getContent());
        popup.setVisible(false);
        popup.setModal(false);
        // Add the markdown class so that it gets styled like rendered markdown, so we are
        // consistent with that. Add quickFilter-tooltip so it get styling consistent
        // with quickfilter popups
        popup.setStyleName("quickFilter-tooltip help-button-tooltip markdown");

        final PopupPosition popupPosition = showHelpEvent.getPopupPosition();

        final String uniqueId = elementToUniqueId(showHelpEvent.getElement());

        PopupUtil.showPopup(
                uniqueId,
                popup,
                false,
                popupPosition,
                null,
                PopupType.POPUP,
                null);
    }

    private static String elementToUniqueId(final Element element) {
        // Convert the position of the element in to a sort of unique position reference
        final long absoluteLeft = element.getAbsoluteLeft();
        final long absoluteTop = element.getAbsoluteTop();
        final String id = absoluteLeft + "_" + absoluteTop;
//        GWT.log("absoluteLeft: " + absoluteLeft + ", absoluteTop: " + absoluteTop + ", id: " + id);
        return id;
    }


    // --------------------------------------------------------------------------------


    private static class HelpPopup extends AbstractPopupPanel implements Popup {

        public HelpPopup(final SafeHtml content) {
            // PopupPanel's constructor takes 'auto-hide' as its boolean parameter.
            // If this is set, the panel closes itself automatically when the user
            // clicks outside of it.
            super(true, false, PopupType.POPUP);

            setWidget(new HTMLPanel(content));
        }

        @Override
        public void setContent(final Widget widget) {
            setWidget(widget);
        }

        @Override
        public void forceHide(final boolean autoClose) {
            super.hide(autoClose);
        }

        @Override
        public void setCaption(final String caption) {
            // No caption
        }

        @Override
        protected void onCloseAction() {
            super.hide(true);
        }
    }
}
