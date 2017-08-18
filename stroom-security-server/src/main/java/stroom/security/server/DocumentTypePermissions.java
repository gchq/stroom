package stroom.security.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.explorer.server.ExplorerService;
import stroom.explorer.shared.DocumentType;
import stroom.security.shared.DocumentPermissionNames;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
class DocumentTypePermissions {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTypePermissions.class);
    private static final String[] STANDARD_PERMISSIONS = new String[]{DocumentPermissionNames.USE,
            DocumentPermissionNames.READ, DocumentPermissionNames.UPDATE, DocumentPermissionNames.DELETE, DocumentPermissionNames.OWNER};
    private static final String[] DASHBOARD_PERMISSIONS = new String[]{DocumentPermissionNames.USE,
            DocumentPermissionNames.READ, DocumentPermissionNames.UPDATE, DocumentPermissionNames.DELETE, DocumentPermissionNames.OWNER, "Download"};

    private final ExplorerService explorerService;

    private String[] folderPermissions;

    @Inject
    DocumentTypePermissions(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    String[] getPermissions(final String type) {
        if ("Folder".equals(type)) {
            return getFolderPermissions();
        }

        if ("Dashboard".equals(type)) {
            return DASHBOARD_PERMISSIONS;
        }

        return STANDARD_PERMISSIONS;
    }

    private String[] getFolderPermissions() {
        if (folderPermissions == null) {
            final List<String> permissionList = new ArrayList<>();
            try {
                final List<DocumentType> documentTypes = explorerService.getDocumentTypes().getAllTypes();
                documentTypes.forEach(documentType -> permissionList.add(DocumentPermissionNames.getDocumentCreatePermission(documentType.getType())));
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            Collections.sort(permissionList);

            permissionList.addAll(Arrays.asList(STANDARD_PERMISSIONS));

            String[] arr = new String[permissionList.size()];
            arr = permissionList.toArray(arr);
            folderPermissions = arr;
        }

        return folderPermissions;
    }
}
