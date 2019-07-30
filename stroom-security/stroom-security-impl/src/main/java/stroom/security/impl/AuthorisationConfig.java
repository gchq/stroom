package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class AuthorisationConfig implements IsConfig {
    private int maxDocumentPermissionCacheSize = 1000;

    @JsonPropertyDescription("The maximum size of the document permissions cache")
    public int getMaxDocumentPermissionCacheSize() {
        return maxDocumentPermissionCacheSize;
    }

    public void setMaxDocumentPermissionCacheSize(final int maxDocumentPermissionCacheSize) {
        this.maxDocumentPermissionCacheSize = maxDocumentPermissionCacheSize;
    }
}
