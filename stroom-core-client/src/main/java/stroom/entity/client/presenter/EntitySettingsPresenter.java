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

package stroom.entity.client.presenter;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.entity.shared.NamedEntity;
import stroom.security.client.ClientSecurityContext;

public abstract class EntitySettingsPresenter<V extends View, E extends NamedEntity> extends EntityEditPresenter<V, E> {
    public EntitySettingsPresenter(final EventBus eventBus, final V view, final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
    }
}
