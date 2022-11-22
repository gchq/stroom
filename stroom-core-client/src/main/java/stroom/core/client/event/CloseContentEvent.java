package stroom.core.client.event;

public class CloseContentEvent {

    private final boolean ignoreIfDirty;
    private final Callback callback;

    public CloseContentEvent(final boolean ignoreIfDirty,
                             final Callback callback) {
        this.ignoreIfDirty = ignoreIfDirty;
        this.callback = callback;
    }

    public boolean isIgnoreIfDirty() {
        return ignoreIfDirty;
    }

    public Callback getCallback() {
        return callback;
    }

    public interface Handler {

        void onCloseRequest(CloseContentEvent event);
    }

    public interface Callback {

        void closeTab(boolean ok);
    }
}