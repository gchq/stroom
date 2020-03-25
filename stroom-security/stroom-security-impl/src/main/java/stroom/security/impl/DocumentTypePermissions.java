package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.security.shared.DocumentPermissionNames;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
class DocumentTypePermissions {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTypePermissions.class);
    private static final List<String> STANDARD_PERMISSIONS = List.of(DocumentPermissionNames.DOCUMENT_PERMISSIONS);
    private static final List<String> DASHBOARD_PERMISSIONS = Stream.concat(STANDARD_PERMISSIONS.stream(), Stream.of("Download")).collect(Collectors.toList());

    private final ExplorerService explorerService;

    private List<String> folderPermissions;

    @Inject
    DocumentTypePermissions(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    List<String> getPermissions(final String type) {
        if (DocumentTypes.isFolder(type)) {
            return getFolderPermissions();
        }

        if ("Dashboard".equals(type)) {
            return DASHBOARD_PERMISSIONS;
        }

        return STANDARD_PERMISSIONS;
    }

    private List<String> getFolderPermissions() {
        if (folderPermissions == null) {
            final List<String> permissionList = new ArrayList<>();
            try {
                final List<DocumentType> documentTypes = explorerService.getNonSystemTypes();
                permissionList.addAll(documentTypes.stream()
                        .map(documentType -> DocumentPermissionNames.getDocumentCreatePermission(documentType.getType()))
                        .sorted()
                        .collect(Collectors.toList()));
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            permissionList.addAll(STANDARD_PERMISSIONS);
            folderPermissions = permissionList;
        }

        return folderPermissions;
    }
}
