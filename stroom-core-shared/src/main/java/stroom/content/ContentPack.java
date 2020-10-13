package stroom.content;

import stroom.util.shared.Version;

public class ContentPack {
    private final ContentPackName name;
    private final Version version;

    public ContentPack(final ContentPackName name, final Version version) {
        this.name = name;
        this.version = version;
    }

    public static ContentPack of(final ContentPackName name, final Version version) {
        return new ContentPack(name, version);
    }

    /**
     * @return Name, e.g. "core-xml-schemas"
     */
    public String getNameAsStr() {
        return name.getPackName();
    }

    /**
     * @return Name, e.g. "core-xml-schemas-v0.2"
     */
    public String getVersionedNameAsStr() {
        return name.getPackName() + "-v" + version.toString();
    }

    ContentPackName getName() {
        return name;
    }

    public Version getVersion() {
        return version;
    }
}
