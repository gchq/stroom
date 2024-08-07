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

package stroom.util.shared;

public class PermissionException extends EntityServiceException {

    private String user;

    public PermissionException() {
    }

    public PermissionException(final String user,
                               final String message) {
        super(message);
        this.user = user;
    }

    public PermissionException(final String user,
                               final String securedType,
                               final String name,
                               final String methodName,
                               final String message) {
        super(message, null, false);
        this.user = user;
        if (securedType != null || name != null || methodName != null) {
            setDetail(ModelStringUtil.toDisplayValue(securedType) + (name == null
                    ? ""
                    : " - " + name)
                    + (methodName == null
                    ? ""
                    : " - " + methodName));
        }
    }

    public String getUser() {
        return user;
    }

    @Override
    public String getMessage() {
        String message = getGenericMessage();
        if (message != null) {
            message = message.replace("You do", "User does");

            if (user != null && !user.isEmpty()) {
                message = message.replace("User does", "User '" + user + "' does");
            }
        }

        return message;
    }

    public String getGenericMessage() {
        return super.getMessage();
    }
}
