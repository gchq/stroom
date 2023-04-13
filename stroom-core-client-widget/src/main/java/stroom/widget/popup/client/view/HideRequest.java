package stroom.widget.popup.client.view;

public class HideRequest {
    private final boolean autoClose;
    private final boolean ok;

    public HideRequest(final boolean autoClose, final boolean ok) {
        this.autoClose = autoClose;
        this.ok = ok;
    }

    public boolean isOk() {
        return ok;
    }

    public boolean isAutoClose() {
        return autoClose;
    }
}
