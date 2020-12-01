package stroom.data.client.presenter;

public enum DisplayMode {
    /**
     * Displays the presenter in a popup modal dialog
     */
    DIALOG("popup"),
    /**
     * Displays the presenter in a top level stroom tab (not a browser tab)
     */
    STROOM_TAB("tab");

    private final String name;

    DisplayMode(final String name) {
        this.name = name;
    }

    public static DisplayMode parse(final String name) {

        for (final DisplayMode displayMode : DisplayMode.values()) {
            if (displayMode.name.equals(name)) {
                return displayMode;
            }
        }
        throw new IllegalArgumentException("Unknown displayMode name" + name);
    }

    public String getName() {
        return name;
    }
}
