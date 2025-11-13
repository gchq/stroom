package stroom.contentstore.shared;

import stroom.docs.shared.Description;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collection;
import java.util.Objects;

/**
 * Separate class to put anything to do with a Content Pack
 * that changes. Important to put this dynamic stuff in a
 * different class so that .hashCode and .equals() work correctly.
 */
@Description(
        "Contains a content pack and the dynamic installation status information"
)
@JsonPropertyOrder({
        "contentPack",
        "installationStatus"
})
@JsonInclude(Include.NON_NULL)
public class ContentStoreContentPackWithDynamicState {

    /** The content pack we're wrapping */
    @JsonProperty
    private final ContentStoreContentPack contentPack;

    /** The dynamic status of this content pack */
    @JsonProperty
    private ContentStoreContentPackStatus installationStatus;

    /**
     * Constructor used when building this in code.
     * getInstallationStatus() will return NOT_INSTALLED.
     * @param contentPack The content store to wrap. Must not be null.
     */
    public ContentStoreContentPackWithDynamicState(
            @JsonProperty("contentPack") final ContentStoreContentPack contentPack) {
        Objects.requireNonNull(contentPack);
        this.contentPack = contentPack;
        this.installationStatus = ContentStoreContentPackStatus.NOT_INSTALLED;
    }

    /**
     * Injected constructor.
     * @param contentPack The content pack we're wrapping.
     *                    Must not be null.
     * @param status The installation status of the content pack.
     *               Can be null in which case the content pack is assumed not
     *               to be installed.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public ContentStoreContentPackWithDynamicState(
            @JsonProperty("contentPack") final ContentStoreContentPack contentPack,
            @JsonProperty("installationStatus") final ContentStoreContentPackStatus status) {
        Objects.requireNonNull(contentPack);
        this.contentPack = contentPack;
        this.installationStatus = status == null ? ContentStoreContentPackStatus.NOT_INSTALLED
                : status;
    }

    /**
     * For test purposes. Not for general use.
     */
    @SerialisationTestConstructor
    public ContentStoreContentPackWithDynamicState() {
        this(new ContentStoreContentPack());
    }

    /**
     * @return The installation status of the content pack, whether not
     * installed, installed or upgradable.
     * Never returns null.
     */
    public ContentStoreContentPackStatus getInstallationStatus() {
        return this.installationStatus;
    }

    /**
     * Used by JSON serialization to set the value of the installation status.
     * @param status The status. Can be null in which case NOT_INSTALLED is assumed.
     */
    public void setInstallationStatus(final ContentStoreContentPackStatus status) {
        this.installationStatus = status == null ? ContentStoreContentPackStatus.NOT_INSTALLED
                : status;
    }

    /**
     * @return The wrapped Content Pack. Never returns null.
     */
    public ContentStoreContentPack getContentPack() {
        return contentPack;
    }

    /**
     * Checks the given collection of GitRepoDocs to see if any of them
     * match this content pack. If one does then it is installed &
     * can be checked for possible upgrades.
     * Result is stored in the object's data.
     * @param docs The collection of GitRepos that already exist. Must
     *             not be null but can be empty.
     */
    public void checkInstallationStatus(final Collection<GitRepoDoc> docs) {
        Objects.requireNonNull(docs);
        ContentStoreContentPackStatus status = ContentStoreContentPackStatus.NOT_INSTALLED;
        for (final GitRepoDoc doc : docs) {
            if (contentPack.matches(doc)) {
                if (contentPack.contentPackUpgrades(doc)) {
                    status = ContentStoreContentPackStatus.PACK_UPGRADABLE;
                } else {
                    status = ContentStoreContentPackStatus.INSTALLED;
                }
                break;
            }
        }

        this.installationStatus = status;
    }

    @Override
    public String toString() {
        return "ContentStoreContentPackWithDynamicState {"
               + "\n    contentPack=" + contentPack
               + "\n    installationStatus=" + installationStatus
               + "\n}";
    }

}
