package stroom.widget.popup.client.view;

public class HideRequest {

    private final DialogAction action;
    private final Runnable cancelHandler;

    public HideRequest(final DialogAction action,
                       final Runnable cancelHandler) {
        this.action = action;
        this.cancelHandler = cancelHandler;
    }

    public boolean isOk() {
        return action == DialogAction.OK;
    }

    public boolean isAutoClose() {
        return action == DialogAction.AUTO_CLOSE;
    }

    public Runnable getCancelHandler() {
        return cancelHandler;
    }
}
