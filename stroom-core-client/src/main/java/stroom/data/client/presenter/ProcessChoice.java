package stroom.data.client.presenter;

public class ProcessChoice {
    private final int priority;
    private final boolean autoPriority;
    private final boolean reprocess;
    private final boolean enabled;

    public ProcessChoice(final int priority,
                         final boolean autoPriority,
                         final boolean reprocess,
                         final boolean enabled) {
        this.priority = priority;
        this.autoPriority = autoPriority;
        this.reprocess = reprocess;
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAutoPriority() {
        return autoPriority;
    }

    public boolean isReprocess() {
        return reprocess;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
