package stroom.app;

import com.google.common.io.Resources;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.servlets.assets.AssetServlet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DynamicAssetsBundle extends AssetsBundle {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicAssetsBundle.class);

    private static final Path assetPath;

    static {
        Path path = null;
        URL url = null;
        try {
            url = Resources.getResource("");
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Unable to get resource URL, we are likely in a jar: " + e.getMessage());
        }

        if (url != null) {
            // Likely only in here if running in development
            try {
                path = Paths.get(url.toURI())
                        .toAbsolutePath();
                while (path != null && !path.getFileName().toString().equals("stroom-app")) {
                    path = path.getParent();
                }
                if (path != null) {
                    path = path.resolve("src").resolve("main").resolve("resources");
                }
            } catch (final URISyntaxException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        assetPath = path;
    }

    public DynamicAssetsBundle(final String resourcePath,
                               final String uriPath,
                               final String indexFile,
                               final String assetsName) {
        super(resourcePath, uriPath, indexFile, assetsName);
    }

    protected AssetServlet createServlet() {
        return new DynamicAssetsServlet(getResourcePath(), getUriPath(), getIndexFile(), StandardCharsets.UTF_8);
    }

    private static class DynamicAssetsServlet extends AssetServlet {

        public DynamicAssetsServlet(String resourcePath,
                                    String uriPath,
                                    @Nullable String indexFile,
                                    @Nullable Charset defaultCharset) {
            super(resourcePath, uriPath, indexFile, defaultCharset);
        }

        @Override
        protected URL getResourceUrl(final String absoluteRequestedResourcePath) {
            if (assetPath != null) {
                final Path path = assetPath.resolve(absoluteRequestedResourcePath);
                try {
                    return path.toUri().toURL();
                } catch (final MalformedURLException e) {
                    e.printStackTrace();
                }
            }

            return super.getResourceUrl(absoluteRequestedResourcePath);
        }
    }
}
