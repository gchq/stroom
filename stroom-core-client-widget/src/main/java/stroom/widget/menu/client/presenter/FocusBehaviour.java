package stroom.widget.menu.client.presenter;

public interface FocusBehaviour {

    /**
     * If the event wants the new item to gain focus then this returns true.
     * This is normally because a keyboard event is the source of the event.
     *
     * @return true if we want to switch focus.
     */
    boolean switchFocus();

    /**
     * If we want to refocus the item that started the event call this method.
     */
    void refocus();
}
