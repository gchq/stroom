package stroom.security.mock;

import stroom.security.api.DocumentPermissionCache;

public class MockDocumentPermissionCache implements DocumentPermissionCache {
    @Override
    public boolean hasDocumentPermission(final String documentUuid, final String permission) {
        return true;
    }
}
