package stroom.contentstore.shared;

/**
 * Used to show the status of a content pack - uninstalled, installed, upgradable.
 * <br/>
 * Note that there are two types of upgrade:
 * <ul>
 *     <li>Pack upgradable, where the Content Store Content Pack has new
 *     settings, so the GitRepoDoc settings must be updated, then any
 *     new content can be installed.</li>
 *     <li>Content upgradable, where the Content Pack settings are the
 *     same, but new content is available within the Git repository.</li>
 * </ul>
 */
public enum ContentStoreContentPackStatus {

    /** Not installed */
    NOT_INSTALLED("-"),

    /** Installed & up to date */
    INSTALLED("Installed"),

    /** Latest content pack has new settings */
    PACK_UPGRADABLE("Pack upgradable"),

    /** Upgrades available with current settings */
    CONTENT_UPGRADABLE("Content upgradable");

    /** Shown in the UI for this enum */
    private final String description;

    /**
     * Constructs the Enum instance.
     * @param description The description to show the user.
     */
    ContentStoreContentPackStatus(final String description) {
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
