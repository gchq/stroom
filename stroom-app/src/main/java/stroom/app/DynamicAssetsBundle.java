package stroom.app;

import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.servlets.assets.AssetServlet;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

public class DynamicAssetsBundle extends AssetsBundle {

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
            final Path path = Paths.get("/home/stroomdev66/work/stroom-master-temp4/stroom-app/src/main/resources/" +
                    absoluteRequestedResourcePath);
            try {
                return path.toUri().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            return super.getResourceUrl(absoluteRequestedResourcePath);
        }
    }
}
