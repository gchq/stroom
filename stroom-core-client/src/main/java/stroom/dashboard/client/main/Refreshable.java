package stroom.dashboard.client.main;

import stroom.dashboard.shared.Automate;

public interface Refreshable {

    void scheduleRefresh();

    void cancelRefresh();

    void setAllowRefresh(boolean allowRefresh);

    boolean isRefreshScheduled();

    boolean isSearching();

    void run(boolean incremental, boolean storeHistory);

    Automate getAutomate();

    boolean isInitialised();
}
