/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.shared;

import stroom.util.shared.GwtNullSafe;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Ideally this would be an enum, except for the fact that we have
// somewhat dynamic 'Create ...' perm names (one for each doc type) that we don't
// really want to hard code in. Shame.

/**
 * Provide string constants for the permissions.
 */
public final class DocumentPermissionNames {

    /**
     * Users with the use permission can use documents.
     */
    public static final String USE = "Use";

    /**
     * Users with the create permission can find and create/saveAs documents.
     */
    public static final String CREATE = "Create";

    /**
     * Users with the read permission can find, create and read/load/view
     * documents.
     */
    public static final String READ = "Read";

    /**
     * Users with the update permission can find, create, read and update/save
     * documents.
     */
    public static final String UPDATE = "Update";

    /**
     * Users with delete permission can find, create, read, update and delete
     * documents.
     */
    public static final String DELETE = "Delete";

    /**
     * Owners have permission to find, create, read, update and delete documents
     * plus they can change permissions.
     */
    public static final String OWNER = "Owner";

    public static final String CREATE_PREFIX = CREATE + " - ";

    public static final String[] DOCUMENT_PERMISSIONS = new String[]{USE, READ, UPDATE, DELETE, OWNER};
    public static final Set<String> DOCUMENT_PERMISSIONS_SET = Collections.unmodifiableSet(
            Arrays.stream(DOCUMENT_PERMISSIONS)
                    .collect(Collectors.toSet()));

    private static final Map<String, String> LOWER_PERMISSIONS = new HashMap<>();
    private static final Map<String, String> HIGHER_PERMISSIONS = new HashMap<>();
    private static final Map<String, PermissionType> PERM_TO_PERM_TYPE_MAP = new HashMap<>();

    static {
        LOWER_PERMISSIONS.put(OWNER, DELETE);
        LOWER_PERMISSIONS.put(DELETE, UPDATE);
        LOWER_PERMISSIONS.put(UPDATE, READ);
        LOWER_PERMISSIONS.put(READ, USE);

        HIGHER_PERMISSIONS.put(DELETE, OWNER);
        HIGHER_PERMISSIONS.put(UPDATE, DELETE);
        HIGHER_PERMISSIONS.put(READ, UPDATE);
        HIGHER_PERMISSIONS.put(USE, READ);

        PERM_TO_PERM_TYPE_MAP.put(USE, PermissionType.NON_DESTRUCTIVE);
        PERM_TO_PERM_TYPE_MAP.put(READ, PermissionType.NON_DESTRUCTIVE);
        PERM_TO_PERM_TYPE_MAP.put(UPDATE, PermissionType.DESTRUCTIVE);
        PERM_TO_PERM_TYPE_MAP.put(DELETE, PermissionType.DESTRUCTIVE);
        PERM_TO_PERM_TYPE_MAP.put(OWNER, PermissionType.DESTRUCTIVE);
    }

    private DocumentPermissionNames() {
        // Constants
    }

    public static String getDocumentCreatePermission(final String docType) {
        return CREATE_PREFIX + docType;
    }

    public static String getTypeFromDocumentCreatePermission(final String permission) {
        return GwtNullSafe.get(
                permission,
                perm -> perm.replace(CREATE_PREFIX, ""));
    }

    public static boolean isDocumentCreatePermission(final String permission) {
        return GwtNullSafe.test(permission, perm -> perm.startsWith(CREATE_PREFIX));
    }

    public static String getLowerPermission(final String permission) {
        return LOWER_PERMISSIONS.get(permission);
    }

    public static String getHigherPermission(final String permission) {
        return HIGHER_PERMISSIONS.get(permission);
    }

    public static boolean isValidPermission(final String permission) {
        for (final String perm : DOCUMENT_PERMISSIONS) {
            if (perm.equals(permission)) {
                return true;
            }
        }

        return false;
    }

    public static InferredPermissions getInferredPermissions(final Collection<String> directPermissions) {
        if (GwtNullSafe.isEmptyCollection(directPermissions)) {
            return InferredPermissions.EMPTY;
        } else {
            final Map<String, InferredPermissionType> permToInferredTypeMap = new HashMap<>();

            for (final String directPermission : directPermissions) {
                // Direct ones can overwrite inferred ones as they trump them
                permToInferredTypeMap.put(directPermission, InferredPermissionType.DIRECT);
                String lowerPermission = getLowerPermission(directPermission);
                // No inferred perms for create doc so no lower perms
                if (!isDocumentCreatePermission(directPermission)) {
                    while (lowerPermission != null) {
                        // May already be a direct one
                        if (!permToInferredTypeMap.containsKey(lowerPermission)) {
                            permToInferredTypeMap.put(lowerPermission, InferredPermissionType.INFERRED);
                        }
                        // Continue down the chain of lower perms
                        lowerPermission = getLowerPermission(lowerPermission);
                    }
                }
            }
            return new InferredPermissions(permToInferredTypeMap);
        }
    }

    public static PermissionType getPermissionType(final String permission) {
        return Objects.requireNonNull(PERM_TO_PERM_TYPE_MAP.get(permission),
                () -> "Unexpected permission '" + permission + "'. Expecting one of ["
                        + PERM_TO_PERM_TYPE_MAP.keySet()
                        .stream()
                        .sorted()
                        .collect(Collectors.joining(", "))
                        + "]");
    }

    public static Set<String> excludeCreatePermissions(final Set<String> perms) {
        return GwtNullSafe.stream(perms)
                .filter(DOCUMENT_PERMISSIONS_SET::contains)
                .collect(Collectors.toSet());
    }

    public static Set<String> excludePermissions(final Set<String> perms,
                                                 final String... excludePerms) {
        if (GwtNullSafe.hasItems(excludePerms)) {
            final Set<String> result = new HashSet<>(perms);
            for (final String excludePerm : excludePerms) {
                result.remove(excludePerm);
            }
            return Collections.unmodifiableSet(result);
        } else {
            return perms;
        }
    }

    // --------------------------------------------------------------------------------


    public enum PermissionType {
        DESTRUCTIVE,
        NON_DESTRUCTIVE
    }


    // --------------------------------------------------------------------------------


//    public static class InferredPermission {
//
//        private final String permission;
//        // NOTE: Inferred state is NOT included in equals/hashcode
//        private final boolean isInferred;
//
//        private InferredPermission(final String permission, final boolean isInferred) {
//            this.permission = Objects.requireNonNull(permission);
//            this.isInferred = isInferred;
//        }
//
//        public static InferredPermission direct(final String permission) {
//            return new InferredPermission(permission, false);
//        }
//
//        public static InferredPermission inferred(final String permission) {
//            return new InferredPermission(permission, true);
//        }
//
//        public String getPermission() {
//            return permission;
//        }
//
//        /**
//         * Permission is inferred from another permission inferred or direct permission
//         */
//        public boolean isInferred() {
//            return isInferred;
//        }
//
//        @Override
//        public boolean equals(final Object o) {
//            if (this == o) {
//                return true;
//            }
//            if (o == null || getClass() != o.getClass()) {
//                return false;
//            }
//            final InferredPermission that = (InferredPermission) o;
//            return Objects.equals(permission, that.permission);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(permission);
//        }
//
//        @Override
//        public String toString() {
//            return permission + (isInferred
//                    ? " (inferred)"
//                    : " (direct)");
//        }
//    }


    // --------------------------------------------------------------------------------


    public static class InferredPermissions {

        private static final InferredPermissions EMPTY = new InferredPermissions(Collections.emptyMap());

        private final Map<String, InferredPermissionType> permToInferredTypeMap;

        private InferredPermissions(final Map<String, InferredPermissionType> permToInferredTypeMap) {
            this.permToInferredTypeMap = Objects.requireNonNull(permToInferredTypeMap);
        }

        public static InferredPermissions empty() {
            return EMPTY;
        }

        public boolean hasPermission(final String permission) {
            return permToInferredTypeMap.containsKey(permission);
        }

        public boolean hasTypedPermission(final String permission, final InferredPermissionType type) {
            Objects.requireNonNull(type);
            if (permission == null) {
                return false;
            } else {
                return GwtNullSafe.get(permToInferredTypeMap.get(permission), type::equals);
            }
        }

        public Optional<InferredPermissionType> getInferredPermissionType(final String permission) {
            return Optional.ofNullable(permToInferredTypeMap.get(permission));
        }

        public Set<String> getPermissionsByType(final InferredPermissionType type) {
            Objects.requireNonNull(type);
            return permToInferredTypeMap.entrySet()
                    .stream()
                    .filter(entry -> type.equals(entry.getValue()))
                    .map(Entry::getKey)
                    .collect(Collectors.toSet());
        }

        public Map<String, InferredPermissionType> asMap() {
            return Collections.unmodifiableMap(permToInferredTypeMap);
        }
    }


    // --------------------------------------------------------------------------------


    public enum InferredPermissionType {
        INFERRED,
        DIRECT;

        public boolean isInferred() {
            return INFERRED.equals(this);
        }

        public boolean isDirect() {
            return DIRECT.equals(this);
        }
    }


    // --------------------------------------------------------------------------------


    // Wrote this enum before remembering about the type specific 'Create ...' perms. doh!!
    // Leaving it here in case we can think of a neater way of passing doc perm names around

//    public enum DocumentPermission implements HasDisplayValue {
//        // --IMPORTANT --
//        // The order of capabilityLevel values is important. Values can be changed if new perms are added
//        // but the capabilityLevel must be set such that a doc perm with a higher capabilityLevel
//        // inherits all doc perms with a lower capabilityLevel. Values do not have to be contiguous
//        // or start from 0. capabilityLevel should not be used outside this class.
//        // --IMPORTANT --
//        USE("Use", 0, PermissionType.NON_DESTRUCTIVE),
//        CREATE("Create", 1, PermissionType.NON_DESTRUCTIVE),
//        READ("Read", 2, PermissionType.NON_DESTRUCTIVE),
//        UPDATE("Update", 3, PermissionType.DESTRUCTIVE),
//        DELETE("Delete", 4, PermissionType.DESTRUCTIVE),
//        OWNER("Owner", 5, PermissionType.DESTRUCTIVE),
//        ;
//
//        private static final Set<DocumentPermission> DOCUMENT_PERMISSIONS_SET =
//                EnumSet.allOf(DocumentPermission.class);
//
//        private static final List<DocumentPermission> SORTED_DOCUMENT_PERMISSIONS = valuesSet().stream()
//                .sorted(Comparator.comparing(docPerm -> docPerm.capabilityLevel))
//                .collect(Collectors.toList());
//
//        private static final Map<DocumentPermission, PermissionType> PERM_TO_PERM_TYPE_MAP =
//                new EnumMap<>(DOCUMENT_PERMISSIONS_SET.stream()
//                        .collect(Collectors.toMap(Function.identity(), DocumentPermission::getPermissionType)));
//
//        static {
//            final List<DocumentPermission> sortedDocPerms = sortedValues();
//            final int cnt = sortedDocPerms.size();
//            for (int i = 0; i < sortedDocPerms.size(); i++) {
//                final DocumentPermission docPerm = sortedDocPerms.get(i);
//                // some will get set twice, but to the same val so is not a big issue as this
//                // is static initialiser
//                if (i >= 1) {
//                    final DocumentPermission prevDocPerm = sortedDocPerms.get(i - 1);
//                    docPerm.previous = prevDocPerm;
//                    prevDocPerm.next = docPerm;
//                }
//                if (i < cnt - 1) {
//                    final DocumentPermission nextDocPerm = sortedDocPerms.get(i + 1);
//                    docPerm.next = nextDocPerm;
//                    nextDocPerm.previous = docPerm;
//                }
//            }
//        }
//
//        private final String displayValue;
//        private final PermissionType permissionType;
//        private final int capabilityLevel;
//
//        private DocumentPermission next;
//        private DocumentPermission previous;
//
//        DocumentPermission(final String displayValue,
//                           final int capabilityLevel,
//                           final PermissionType permissionType) {
//            this.displayValue = displayValue;
//            this.permissionType = permissionType;
//            this.capabilityLevel = capabilityLevel;
//        }
//
//        /**
//         * @return The {@link DocumentPermission} with a higher capability than this or null
//         * if this is the highest.
//         */
//        public DocumentPermission getHigherPermission() {
//            return next;
//        }
//
//        /**
//         * @return The {@link DocumentPermission} with a lower capability than this or null
//         * if this is the lowest.
//         */
//        public DocumentPermission getLowerPermission() {
//            return previous;
//        }
//
//        /**
//         * @return The value used for display and DB persistence.
//         */
//        public String getDisplayValue() {
//            return displayValue;
//        }
//
//        public PermissionType getPermissionType() {
//            return permissionType;
//        }
//
//        /**
//         * @return The {@link DocumentPermission#values()} as a {@link Set}.
//         */
//        public static Set<DocumentPermission> valuesSet() {
//            return DOCUMENT_PERMISSIONS_SET;
//        }
//
//        /**
//         * In ascending order of capability
//         */
//        public static List<DocumentPermission> sortedValues() {
//            return SORTED_DOCUMENT_PERMISSIONS;
//        }
//
//        public static DocumentPermission getLowerPermission(final DocumentPermission permission) {
//            return GwtNullSafe.get(permission, docPerm -> docPerm.getLowerPermission());
//        }
//
//        public static DocumentPermission getHigherPermission(final DocumentPermission permission) {
//            return GwtNullSafe.get(permission, docPerm -> docPerm.getHigherPermission());
//        }
//    }
}
