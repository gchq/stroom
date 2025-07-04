package stroom.contentstore.shared;

import stroom.docs.shared.Description;

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
    private final String username;

    @JsonProperty
    private final String password;

    /**
     * Constructor for content pack. Called by JSON serialisation system.
     * @param contentPack The content pack to create the GitRepoDoc from.
     *                    Must never be null.
     * @param username The username, if authentication is required. Can be null.
     * @param password The password, if authentication is required. Can be null.
     */
    @JsonCreator
    @SuppressWarnings("unused")
    public ContentStoreCreateGitRepoRequest(@JsonProperty("contentPack") final ContentStoreContentPack contentPack,
                                            @JsonProperty("username") final String username,
                                            @JsonProperty("password") final String password) {
        Objects.requireNonNull(contentPack);
        this.contentPack = contentPack;
        final boolean gitNeedsAuth = contentPack.getGitNeedsAuth();
        this.username = gitNeedsAuth && username != null ? username : "";
        this.password = gitNeedsAuth && password != null ? password : "";
    }

    /**
     * @return The content pack. Never returns null.
     */
    public ContentStoreContentPack getContentPack() {
        return contentPack;
    }

    /**
     * @return The username, if getGitNeedsAuth() returns true.
     * Never returns null but may return empty string.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The password, if getGitNeedsAuth() returns true.
     * Never returns null but may return empty string.
     */
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentStoreCreateGitRepoRequest that = (ContentStoreCreateGitRepoRequest) o;
        return Objects.equals(contentPack, that.contentPack) && Objects.equals(username,
                that.username) && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentPack, username, password);
    }

    @Override
    public String toString() {
        return "ContentStoreCreateGitRepoRequest{" +
               "\n   contentPack=" + contentPack +
               ",\n   username='" + username +
               "'\n}";
    }
}
