/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl;

import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolPolicyResource;
import stroom.ai.shared.AiToolSummary;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class AiToolPolicyResourceImpl implements AiToolPolicyResource {

    private final Provider<AiToolPolicyService> serviceProvider;

    @Inject
    AiToolPolicyResourceImpl(final Provider<AiToolPolicyService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public List<AiToolPolicy> list() {
        return serviceProvider.get().list();
    }

    @Override
    public AiToolPolicy fetch(final Integer id) {
        return serviceProvider.get().fetch(id);
    }

    @Override
    public AiToolPolicy create(final AiToolPolicy policy) {
        return serviceProvider.get().create(policy);
    }

    @Override
    public AiToolPolicy update(final int id, final AiToolPolicy policy) {
        return serviceProvider.get().update(id, policy);
    }

    @Override
    public boolean delete(final int id) {
        return serviceProvider.get().delete(id);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public List<AiToolSummary> listTools() {
        return serviceProvider.get().listTools();
    }
}
