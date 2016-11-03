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

package stroom.entity.shared;

import stroom.util.shared.ModelStringUtil;

public class PermissionException extends EntityServiceException {
    private static final long serialVersionUID = -7671344466028839328L;

    public PermissionException() {
        // Default constructor necessary for GWT serialisation.
    }

    public PermissionException(final String message) {
        super(message);
    }

    public PermissionException(final String securedType, final String name, final String methodName,
            final String message) {
        super(message, null, false);
        if (securedType != null || name != null || methodName != null) {
            setDetail(ModelStringUtil.toDisplayValue(securedType) + (name == null ? "" : " - " + name)
                    + (methodName == null ? "" : " - " + methodName));
        }
    }

    public static PermissionException createLoginRequiredException(final String name,
            final String methodName) {
        return new PermissionException(null, name, methodName, "User must be logged in to call service");
    }

    public static PermissionException createAppPermissionRequiredException(final String permission,
                                                                           final String methodName) {
        return new PermissionException(null, permission, methodName,
                "User does not have the required permission (" + permission + ")");
    }

//    public static PermissionException createDocumentTypePermissionRequiredException(final String type, final String permission,
//                                                                               final String methodName) {
//        return new PermissionException(type, permission, methodName,
//                "User does not have the required permission (" + permission + ") for document type: " + type);
//    }

    public static PermissionException createDocumentPermissionRequiredException(final Document document, final String permission,
                                                                                final String methodName) {
        return new PermissionException(document.getType(), permission, methodName,
                "User does not have the required permission (" + permission + ") on document (type=" + document.getType() + ", uuid=" + document.getUuid() + ")");
    }
}
