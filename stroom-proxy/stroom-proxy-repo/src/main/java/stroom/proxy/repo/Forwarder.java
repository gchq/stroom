package stroom.proxy.repo;

public interface Forwarder extends HasShutdown {

    /**
     * Cleanup as part of the start up process in case aggregation has been turned on/off.
     */
    int cleanup();

    void forward();

    int retryFailures();

    void addChangeListener(ChangeListener changeListener);

    interface ChangeListener {

        void onChange();
    }
}
