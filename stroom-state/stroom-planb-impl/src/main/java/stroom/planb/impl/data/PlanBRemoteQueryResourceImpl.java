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

package stroom.planb.impl.data;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class PlanBRemoteQueryResourceImpl implements PlanBRemoteQueryResource {

    private final Provider<PlanBQueryService> planBQueryServiceProvider;
    private final Provider<PlanBShardInfoServiceImpl> planBShardInfoServiceProvider;

    @Inject
    public PlanBRemoteQueryResourceImpl(final Provider<PlanBQueryService> planBQueryServiceProvider,
                                        final Provider<PlanBShardInfoServiceImpl> planBShardInfoServiceProvider) {
        this.planBQueryServiceProvider = planBQueryServiceProvider;
        this.planBShardInfoServiceProvider = planBShardInfoServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PlanBValue getValue(final GetRequest request) {
        return planBQueryServiceProvider.get().getPlanBValue(request);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PlanBShardInfoResponse getStoreInfo(final PlanBShardInfoRequest request) {
        return new PlanBShardInfoResponse(planBShardInfoServiceProvider.get().getStoreInfo(request.getFields()));
    }
}
