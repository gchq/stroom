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

package stroom.headless;

import org.springframework.stereotype.Component;
import stroom.security.SecurityContext;

@Component
public class HeadlessSecurityContext implements SecurityContext {
    @Override
    public void pushUser(final String name) {
        // Do nothing.
    }

    @Override
    public String popUser() {
        return null;
    }

    @Override
    public String getUserId() {
        return "admin";
    }

    @Override
    public String getApiToken() {
        return null;
    }

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public boolean isAdmin() {
        return true;
    }

    @Override
    public void elevatePermissions() {
    }

    @Override
    public void restorePermissions() {
    }

    @Override
    public boolean hasAppPermission(final String permission) {
        return true;
    }

    @Override
    public boolean hasDocumentPermission(final String documentType, final String documentId, final String permission) {
        return true;
    }

    @Override
    public void clearDocumentPermissions(final String documentType, final String documentUuid) {
    }

    @Override
    public void addDocumentPermissions(final String sourceType, final String sourceUuid, final String documentType, final String documentUuid, final boolean owner) {
    }
}
