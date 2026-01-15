/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.util.shared.UserDesc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

// Intended to be inherited by other resource interfaces
public interface ApiKeyCheckResource {

    String VERIFY_API_KEY_PATH_PART = "/verifyApiKey";

    @SuppressWarnings("unused") // Called by proxy
    @POST
    @Path(ApiKeyCheckResource.VERIFY_API_KEY_PATH_PART)
    @Operation(
            summary = "Check if the passed API key is valid",
            operationId = "findApiKeysByCriteria")
    UserDesc verifyApiKey(@Parameter(description = "request", required = true) VerifyApiKeyRequest request);
}
