package stroom.svg.client;

public enum IconColour {
    BLUE("icon-colour__blue"),
    GREY("icon-colour__grey"),
    GREEN("icon-colour__green"),
    RED("icon-colour__red");


    private final String className;

    IconColour(final String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
}
