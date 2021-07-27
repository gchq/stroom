package stroom.widget.popup.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

public interface HideUiHandlers extends UiHandlers {
    default void onHideRequest(boolean autoClose, boolean ok) {
    }
}
