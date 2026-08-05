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

package stroom.planb.impl.pipeline;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.GetRequest;
import stroom.planb.impl.data.PlanBQueryService;
import stroom.planb.shared.PlanBDoc;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * getState() is used from the query language, where a failure has to be reported as a value rather than thrown,
 * so that it surfaces in the result rather than killing the whole query. See gh-5689 and gh-5657.
 */
class TestStateProviderImpl {

    @Mock
    private PlanBDocCache planBDocCache;
    @Mock
    private PlanBQueryService planBQueryService;
    @Mock
    private SecurityContext securityContext;

    private AutoCloseable mocks;
    private StateProviderImpl stateProvider;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(securityContext.useAsReadResult(any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(0).get());
        stateProvider = new StateProviderImpl(planBDocCache, planBQueryService, securityContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void unavailableSnapshotIsReportedAsAnErrorValue() {
        when(planBDocCache.get(any())).thenReturn(doc());
        when(planBQueryService.getVal(any(GetRequest.class)))
                .thenThrow(new RuntimeException("404 Not Found - No snapshot has been created yet"));

        final Val val = stateProvider.getState("myMap", "key", 0L);

        assertThat(val.type()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains("No snapshot has been created yet");
    }

    @Test
    void workingSnapshotReturnsTheValue() {
        when(planBDocCache.get(any())).thenReturn(doc());
        when(planBQueryService.getVal(any(GetRequest.class))).thenReturn(ValString.create("value"));

        final Val val = stateProvider.getState("myMap", "key", 0L);

        assertThat(val.type()).isNotEqualTo(Type.ERR);
        assertThat(val.toString()).isEqualTo("value");
    }

    /**
     * A missing state doc is not an error, it just means this provider has no value for the map.
     */
    @Test
    void missingStateDocIsNotAnError() {
        when(planBDocCache.get(any())).thenReturn(null);

        final Val val = stateProvider.getState("myMap", "key", 0L);

        assertThat(val.type()).isEqualTo(Type.NULL);
    }


    /**
     * The StateProvider contract is that getState never returns null, which the getState() expression
     * function relies on. A null from the query service must surface as ValNull, not null.
     */
    @Test
    void nullValueIsReportedAsValNull() {
        when(planBDocCache.get(any())).thenReturn(doc());
        when(planBQueryService.getVal(any(GetRequest.class))).thenReturn(null);

        final Val val = stateProvider.getState("myMap", "key", 0L);

        assertThat(val).isNotNull();
        assertThat(val.type()).isEqualTo(Type.NULL);
    }

    private PlanBDoc doc() {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("myMap").build();
    }
}
