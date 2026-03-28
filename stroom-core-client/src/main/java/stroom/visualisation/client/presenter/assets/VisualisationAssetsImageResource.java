package stroom.visualisation.client.presenter.assets;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeUri;

/**
 * Implements the ImageResource for the tree.
 */
public class VisualisationAssetsImageResource implements ImageResource {

    /**
     * Height of the image in the img tag
     */
    private final int height;

    /**
     * Width of the image in the img tag
     */
    private final int width;

    /**
     * The URL of the image to display
     */
    private final String url;

    /**
     * Constructor
     * @param height Height of the image within the img tag.
     * @param width  Width of the image within the img tag.
     * @param url    Url of the image. Not checked or filtered in any way so must be safe.
     */
    public VisualisationAssetsImageResource(final int height,
                                            final int width,
                                            final String url) {
        this.height = height;
        this.width = width;
        this.url = url;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getLeft() {
        return 0;
    }

    @Override
    public SafeUri getSafeUri() {
        return new AssetSafeUri(url);
    }

    @Override
    public int getTop() {
        return 0;
    }

    @Override
    public String getURL() {
        return url;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public boolean isAnimated() {
        return false;
    }

    @Override
    public String getName() {
        return "";
    }

    // --------------------------------------------------------------------------------
    /**
     * Implementation of the SafeUri class for the tree.
     * Doesn't do any filtering or checking.
     */
    private static class AssetSafeUri implements SafeUri {

        private final String uri;

        public AssetSafeUri(final String uri) {
            this.uri = uri;
        }

        @Override
        public String asString() {
            return uri;
        }

    }

}
