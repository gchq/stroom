package stroom.contentstore.shared;

import stroom.docs.shared.Description;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Holds all the data needed for a request to create a GitRepoDoc from
 * an ContentStoreContentPack.
 */
@Description(
        "Holds the information for submitting a request to create a "
        + "GitRepoDoc from an ContentStoreContentPack."
)
@JsonPropertyOrder({
        "contentPack",
        "username",
        "password"
})
@JsonInclude(Include.NON_NULL)
public class ContentStoreCreateGitRepoRequest {

    @JsonProperty
    private final ContentStoreContentPack contentPack;

    @JsonProperty
    private final String credentialsId;

    /**
     * Constructor for content pack. Called by JSON serialisation system.
     * @param contentPack The content pack to create the GitRepoDoc from.
     *                    Must never be null.
     * @param credentialsId The credentialsId, if authentication is required. Can be null.
     */
    @JsonCreator
    @SuppressWarnings("unused")
    public ContentStoreCreateGitRepoRequest(@JsonProperty("contentPack") final ContentStoreContentPack contentPack,
                                            @JsonProperty("credentialsId") final String credentialsId) {
        Objects.requireNonNull(contentPack);
        this.contentPack = contentPack;
        final boolean gitNeedsAuth = contentPack.getGitNeedsAuth();
        this.credentialsId = gitNeedsAuth && credentialsId != null ? credentialsId : "";
    }

    /**
     * For test purposes. Not for general use.
     */
    @SerialisationTestConstructor
    public ContentStoreCreateGitRepoRequest() {
        this(new ContentStoreContentPack(),
                "credentialsId");
    }

    /**
     * @return The content pack. Never returns null.
     */
    public ContentStoreContentPack getContentPack() {
        return contentPack;
    }

    /**
     * @return The credentialsId, if getGitNeedsAuth() returns true.
     * Never returns null but may return empty string.
     */
    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentStoreCreateGitRepoRequest that = (ContentStoreCreateGitRepoRequest) o;
        return Objects.equals(contentPack, that.contentPack)
               && Objects.equals(credentialsId, that.credentialsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentPack, credentialsId);
    }

    @Override
    public String toString() {
        return "ContentStoreCreateGitRepoRequest{" +
               "\n   contentPack=" + contentPack +
               ",\n   username='" + credentialsId +
               "'\n}";
    }
}
