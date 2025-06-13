package stroom.appstore.shared;

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
        "ownerName",
        "ownerUrl",
        "ownerDescription"
})
@JsonInclude(Include.NON_NULL)
public class ContentStoreMetadata {
    /** Name of this Content Store */
    @JsonProperty("ownerName")
    private final String ownerName;

    /** URL of the content store owner */
    @JsonProperty("ownerUrl")
    private final String ownerUrl;

    /** Description of this content store, in markdown */
    @JsonProperty("ownerDescription")
    private final String ownerDescription;

    /**
     * Called by YAML parser to construct the metadata associated with
     * a Content Store.
     * @param ownerName The name of the owner of the content store.
     *                  Must not be null.
     * @param ownerUrl The URL associated with the owner. Can be null.
     * @param ownerDescription The description of the content store in
     *                         markdown. Can be null.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public ContentStoreMetadata(@JsonProperty("ownerName") final String ownerName,
                                @JsonProperty("ownerUrl") final String ownerUrl,
                                @JsonProperty("ownerDescription") final String ownerDescription) {
        Objects.requireNonNull(ownerName);
        this.ownerName = ownerName;
        this.ownerUrl = ownerUrl == null ? "" : ownerUrl;
        this.ownerDescription = ownerUrl == null ? "" : ownerDescription;
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentStoreMetadata that = (ContentStoreMetadata) o;
        return Objects.equals(ownerName, that.ownerName)
               && Objects.equals(ownerUrl, that.ownerUrl)
               && Objects.equals(ownerDescription, that.ownerDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ownerName,
                ownerUrl,
                ownerDescription);
    }

    @Override
    public String toString() {
        return "ContentStoreMetadata{" +
               "ownerName='" + ownerName + '\'' +
               ", ownerUrl='" + ownerUrl + '\'' +
               ", ownerDescription='" + ownerDescription + '\'' +
               '}';
    }

}
