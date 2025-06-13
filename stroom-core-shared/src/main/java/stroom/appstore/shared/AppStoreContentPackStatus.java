package stroom.appstore.shared;

/**
 * Used to show the status of a content pack - uninstalled, installed, upgradable.
 */
public enum AppStoreContentPackStatus {

    /** Not installed */
    UNINSTALLED("-"),

    /** Installed & up to date */
    INSTALLED("Installed"),

    /** Upgrades available */
    UPGRADABLE("Upgradable");

    /** Shown in the UI for this enum */
    private final String description;

    /**
     * Constructs the Enum instance.
     * @param description The description to show the user.
     */
    AppStoreContentPackStatus(final String description) {
        this.description = description;
    }

    /**
     * Returns the description of this state.
     */
    @Override
    public String toString() {
        return description;
    }

}
