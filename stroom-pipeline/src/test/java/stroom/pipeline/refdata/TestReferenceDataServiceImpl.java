/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.refdata;

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReferenceDataServiceImpl {

    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipe-uuid", "loader");

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);

    @Test
    void loaderPipelineWithoutUsePermissionIsRejected() {
        Mockito.when(securityContext.hasDocumentPermission(PIPELINE, DocumentPermission.USE))
                .thenReturn(false);

        assertThatThrownBy(() ->
                ReferenceDataServiceImpl.requireUsePermissionIfPresent(securityContext, PIPELINE))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void loaderPipelineWithUsePermissionIsAllowed() {
        Mockito.when(securityContext.hasDocumentPermission(PIPELINE, DocumentPermission.USE))
                .thenReturn(true);

        assertThatCode(() ->
                ReferenceDataServiceImpl.requireUsePermissionIfPresent(securityContext, PIPELINE))
                .doesNotThrowAnyException();
    }

    @Test
    void nullLoaderPipelineIsLeftForDownstreamValidation() {
        assertThatCode(() ->
                ReferenceDataServiceImpl.requireUsePermissionIfPresent(securityContext, null))
                .doesNotThrowAnyException();
        Mockito.verifyNoInteractions(securityContext);
    }
}
