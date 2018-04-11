package stroom.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.explorer.ExplorerService;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.security.shared.DocumentPermissionNames;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
class DocumentTypePermissions {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTypePermissions.class);
    private static final String[] STANDARD_PERMISSIONS = DocumentPermissionNames.DOCUMENT_PERMISSIONS;
    private static final String[] DASHBOARD_PERMISSIONS = Stream.concat(Stream.of(STANDARD_PERMISSIONS), Stream.of("Download")).toArray(String[]::new);

    private final ExplorerService explorerService;

    private String[] folderPermissions;

    @Inject
    DocumentTypePermissions(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    String[] getPermissions(final String type) {
        if (DocumentTypes.isFolder(type)) {
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
                final List<DocumentType> documentTypes = explorerService.getDocumentTypes().getNonSystemTypes();
                permissionList.addAll(documentTypes.stream()
                        .map(documentType -> DocumentPermissionNames.getDocumentCreatePermission(documentType.getType()))
                        .collect(Collectors.toList()));
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
