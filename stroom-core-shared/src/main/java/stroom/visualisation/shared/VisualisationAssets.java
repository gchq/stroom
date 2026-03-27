package stroom.visualisation.shared;

import stroom.docs.shared.Description;
import stroom.util.shared.ResourceKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Packages all the stuff about visualisation assets in one object
 * for uploading to the server.
 */
@Description(
        "Packages all the assets in one place"
)
@JsonPropertyOrder({
        "ownerId",
        "dirty",
        "assets",
        "uploadedFiles"
})
@JsonInclude(Include.NON_NULL)
public class VisualisationAssets {

    @JsonProperty
    private final String ownerId;

    @JsonProperty
    private final Boolean dirty;

    @JsonProperty
    private final List<VisualisationAsset> assets = new ArrayList<>();

    @JsonProperty
    private final Map<String, ResourceKey> uploadedFiles = new HashMap<>();

    /**
     * Constructor used in client.
     * @param ownerId Document that owns these assets. Must not be null.
     */
    public VisualisationAssets(final String ownerId) {
        this(ownerId, null);
    }

    /**
     * Constructor used in client.
     * @param ownerId Document that owns these assets. Must not be null.
     * @param uploadedFiles Files that have been uploaded since last save. Can be null.
     */
    public VisualisationAssets(final String ownerId,
                               final Map<String, ResourceKey> uploadedFiles) {
        this(ownerId, false, uploadedFiles, null);
    }

    /**
     * Constructor used for serialisation.
     * @param ownerId       Document that owns these assets. Must not be null.
     * @param dirty         Whether the data is dirty and needs saving.
     * @param uploadedFiles Files that have been uploaded since last save. Can be null.
     * @param assets        List of all assets. Can be null.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public VisualisationAssets(@JsonProperty("ownerId") final String ownerId,
                               @JsonProperty("dirty") final boolean dirty,
                               @JsonProperty("uploadedFiles") final Map<String, ResourceKey> uploadedFiles,
                               @JsonProperty("assets") final Collection<VisualisationAsset> assets) {
        this.ownerId = ownerId;
        this.dirty = dirty;
        if (uploadedFiles != null) {
            this.uploadedFiles.putAll(uploadedFiles);
        }
        if (assets != null) {
            this.assets.addAll(assets);
        }
    }

    /**
     * @return The ID of the document that owns these assets.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Note that the server stores 'draft' data for the visualisation assets in a separate table.
     * Thus, when the server returns the data to the client it may be dirty and need saving.
     * Saving it means copying from the draft table to the live table.
     * @return true if the data is dirty and needs saving. False if everything is up to date.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Adds an asset to the internal collection of assets.
     */
    public void addAsset(final VisualisationAsset asset) {
        assets.add(asset);
    }

    /**
     * Adds lots of assets to the internal collection of assets.
     */
    public void addAllAssets(final Collection<VisualisationAsset> assets) {
        this.assets.addAll(assets);
    }

    /**
     * @return Unmodifiable collection of assets that have been added.
     *         Never returns null.
     */
    public List<VisualisationAsset> getAssets() {
        return Collections.unmodifiableList(assets);
    }

    /**
     * @return The map of asset ID to uploaded file resource keys.
     *         Never returns null.
     */
    public Map<String, ResourceKey> getUploadedFiles() {
        return Collections.unmodifiableMap(uploadedFiles);
    }

    @Override
    public String toString() {
        return "VisualisationAssets{" +
               "\nownerId=" + ownerId +
               ", \ndirty=" + dirty +
               ", \nassets=" + assets +
               ", \nuploadedFiles=" + uploadedFiles +
               "\n}";
    }
}
