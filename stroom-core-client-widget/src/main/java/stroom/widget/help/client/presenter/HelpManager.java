package stroom.widget.help.client.presenter;

import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.AbstractPopupPanel;
import stroom.widget.popup.client.view.Popup;
import stroom.widget.popup.client.view.PopupUtil;
import stroom.widget.tooltip.client.event.ShowHelpEvent;

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

        PopupUtil.showPopup(
                popup,
                false,
                popupPosition,
                null,
                PopupType.POPUP,
                null);
    }


    // --------------------------------------------------------------------------------


    private static class HelpPopup extends AbstractPopupPanel implements Popup {

        public HelpPopup(final SafeHtml content) {
            // PopupPanel's constructor takes 'auto-hide' as its boolean parameter.
            // If this is set, the panel closes itself automatically when the user
            // clicks outside of it.
            super(e -> {
            }, true, false);

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
        public void setIcon(final SvgImage icon) {
            // No icon
        }

        @Override
        public void setCaption(final String caption) {
            // No caption
        }
    }
}
