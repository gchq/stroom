package stroom.contentstore.shared;

import stroom.docs.shared.Description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Holds metadata associated with a content store.
 */
@Description(
        "Contains the metadata for a Content Store"
)
@JsonPropertyOrder({
        "ownerId",
        "ownerName",
        "ownerUrl",
        "ownerDescription",
        "authContact"
})
@JsonInclude(Include.NON_NULL)
public class ContentStoreMetadata {

    /**
     * ID of this Content Store
     */
    @JsonProperty("ownerId")
    private final String ownerId;

    /**
     * Name of this Content Store
     */
    @JsonProperty("ownerName")
    private final String ownerName;

    /**
     * URL of the content store owner
     */
    @JsonProperty("ownerUrl")
    private final String ownerUrl;

    /**
     * Description of this content store, in markdown
     */
    @JsonProperty("ownerDescription")
    private final String ownerDescription;

    /**
     * Markdown text describing how to get authentication credentials
     */
    @JsonProperty("authContact")
    private final String authContact;

    /**
     * Length to truncate fields to in toString()
     */
    private static final int TRUNC = 10;

    private static final String ERR_OWNER_ID = "Error in Content Store specification: "
                                               + "contentStore.meta.ownerId must be specified";

    private static final String ERR_OWNER_NAME = "Error in Content Store specification: "
                                                 + "contentStore.meta.ownerName must be specified";

    /**
     * Called by YAML parser to construct the metadata associated with
     * a Content Store.
     *
     * @param ownerId          The ID of the owner of the content store. Must be
     *                         globally unique so should probably be the domain
     *                         name of the owner.
     * @param ownerName        The name of the owner of the content store.
     *                         Must not be null.
     * @param ownerUrl         The URL associated with the owner. Can be null.
     * @param ownerDescription The description of the content store in
     *                         markdown. Can be null.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public ContentStoreMetadata(@JsonProperty("ownerId") final String ownerId,
                                @JsonProperty("ownerName") final String ownerName,
                                @JsonProperty("ownerUrl") final String ownerUrl,
                                @JsonProperty("ownerDescription") final String ownerDescription,
                                @JsonProperty("authContact") final String authContact) {
        Objects.requireNonNull(ownerId, ERR_OWNER_ID);
        Objects.requireNonNull(ownerName, ERR_OWNER_NAME);
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.ownerUrl = ownerUrl == null
                ? ""
                : ownerUrl;
        this.ownerDescription = ownerUrl == null
                ? ""
                : ownerDescription;
        this.authContact = authContact == null
                ? ""
                : authContact;
    }

    /**
     * @return The ID of the owner of this content store.
     * Never returns null.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * @return The name of the owner of this content store.
     * Never returns null.
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * @return The URL associated with the owner of this content store.
     * Never returns null.
     */
    public String getOwnerUrl() {
        return ownerUrl;
    }

    /**
     * @return The Markdown description associated with the owner of this
     * content store. Never returns null.
     */
    public String getOwnerDescription() {
        return ownerDescription;
    }

    /**
     * @return The markdown content that describes how to get authentication
     * credentials for Content Packs marked gitNeedsAuth: true.
     */
    public String getAuthContact() {
        return authContact;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentStoreMetadata that = (ContentStoreMetadata) o;
        return Objects.equals(ownerId, that.ownerId)
               && Objects.equals(ownerName, that.ownerName)
               && Objects.equals(ownerUrl, that.ownerUrl)
               && Objects.equals(ownerDescription, that.ownerDescription)
               && Objects.equals(authContact, that.authContact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ownerId,
                ownerName,
                ownerUrl,
                ownerDescription,
                authContact);
    }

    @Override
    public String toString() {
        return "ContentStoreMetadata{" +
               "\n  ownerId=" + ownerId +
               "'\n  ownerName='" + ownerName +
               "'\n  ownerUrl='" + ownerUrl +
               "'\n  ownerDescription='" + ownerDescription.substring(0, Math.min(ownerDescription.length(), TRUNC)) +
               "'\n  authContact='" + authContact.substring(0, Math.min(authContact.length(), TRUNC)) +
               "'\n}";
    }
}
