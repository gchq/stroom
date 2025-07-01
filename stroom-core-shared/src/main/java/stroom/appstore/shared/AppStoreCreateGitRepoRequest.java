package stroom.appstore.shared;

import stroom.docs.shared.Description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Holds all the data needed for a request to create a GitRepoDoc from
 * an AppStoreContentPack.
 */
@Description(
        "Holds the information for submitting a request to create a "
        + "GitRepoDoc from an AppStoreContentPack."
)
@JsonPropertyOrder({
        "contentPack",
        "autoPull"
})
@JsonInclude(Include.NON_NULL)
public class AppStoreCreateGitRepoRequest {

    @JsonProperty
    private final AppStoreContentPack contentPack;

    @JsonProperty
    private final Boolean autoPull;

    /**
     * Constructor for content pack. Called by JSON serialisation system.
     * @param contentPack The content pack to create the GitRepoDoc from.
     *                    Must never be null.
     * @param autoPull Whether to automatically pull the content after the
     *                 GitRepoDoc has been created. Can be null in which case
     *                 FALSE is assumed.
     */
    @JsonCreator
    @SuppressWarnings("unused")
    public AppStoreCreateGitRepoRequest(@JsonProperty("contentPack") final AppStoreContentPack contentPack,
                                 @JsonProperty("autoPull") final Boolean autoPull) {
        Objects.requireNonNull(contentPack);
        this.contentPack = contentPack;
        this.autoPull = autoPull == null ? Boolean.FALSE : autoPull;
    }

    /**
     * @return The content pack. Never returns null.
     */
    public AppStoreContentPack getContentPack() {
        return contentPack;
    }

    /**
     * @return Whether to automatically pull content when the GitRepoDoc is
     * created. Never returns null.
     */
    public Boolean getAutoPull() {
        return autoPull;
    }
}
