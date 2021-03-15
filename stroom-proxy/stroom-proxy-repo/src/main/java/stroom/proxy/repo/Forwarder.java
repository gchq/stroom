package stroom.proxy.repo;

public interface Forwarder extends HasShutdown {

    void forward();

    void addChangeListener(ChangeListener changeListener);

    interface ChangeListener {

        void onChange();
    }
}
