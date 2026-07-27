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

package stroom.dashboard.impl;

import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TestDashboardServiceImpl {

    private static final DocRef DATA_SOURCE = new DocRef("LuceneIndex", "ds-uuid", "index");

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final DashboardServiceImpl service = new DashboardServiceImpl(
            null, null, null, null, null, null, securityContext,
            null, null, null, null, null, null, null, null);

    @Test
    void downloadQueryRequiresUsePermissionOnTheDataSource() {
        // secureResult(supplier) just runs the supplier (login is assumed by the wrapper).
        when(securityContext.secureResult(any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(securityContext.hasDocumentPermission(DATA_SOURCE, DocumentPermission.USE))
                .thenReturn(false);

        final DashboardSearchRequest request = DashboardSearchRequest.builder()
                .search(Search.builder().dataSourceRef(DATA_SOURCE).build())
                .build();

        assertThatThrownBy(() -> service.downloadQuery(request))
                .isInstanceOf(PermissionException.class);
    }
}
