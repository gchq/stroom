package stroom.appstore.impl;

import stroom.appstore.shared.AppStoreContentPack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class to represent content packs from one URL.
 * Used to pull the content packs in from the YAML App Store file.
 */
public class ContentPacks {

    /** Name of this Content Store */
    @JsonProperty("uiName")
    private final String uiName;

    /** URL of the content store owner */
    @JsonProperty("ownerUrl")
    private final String ownerUrl;

    /** Description of this content store */
    @JsonProperty("ownerDescription")
    String ownerDescription;

    /** List of git repositories */
    @JsonProperty("contentPacks")
    private final List<AppStoreContentPack> contentPacks = new ArrayList<>();

    /**
     * Constructor. Called from YAML parser.
     */
    @SuppressWarnings("unused")
    public ContentPacks(@JsonProperty("uiName") final String uiName,
                        @JsonProperty("ownerUrl") final String ownerUrl,
                        @JsonProperty("ownerDescription") final String ownerDescription,
                        @JsonProperty("contentPacks") final List<AppStoreContentPack> contentPacks) {
        this.uiName = uiName;
        this.ownerUrl = ownerUrl;
        this.ownerDescription = ownerDescription;
        if (contentPacks != null) {
            this.contentPacks.addAll(contentPacks);
        }
    }

    public String getUiName() {
        return uiName;
    }

    public String getOwnerUrl() {
        return ownerUrl;
    }

    public String getOwnerDescription() {
        return ownerDescription;
    }

    public List<AppStoreContentPack> getContentPacks() {
        return Collections.unmodifiableList(contentPacks);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentPacks that = (ContentPacks) o;
        return Objects.equals(uiName, that.uiName)
                && Objects.equals(ownerUrl, that.ownerUrl)
                && Objects.equals(ownerDescription, that.ownerDescription)
                && Objects.equals(contentPacks, that.contentPacks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                uiName,
                ownerUrl,
                ownerDescription,
                contentPacks);
    }

    @Override
    public String toString() {
        return "ContentPacks {"
                + "\n  uiName=" + uiName
                + "\n  ownerUrl=" + ownerUrl
                + "\n  ownerDescription=" + ownerDescription
                + "\n  contentPacks =" + contentPacks +
               "\n}";
    }
}
