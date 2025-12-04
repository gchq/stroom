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

package stroom.annotation.client;

import stroom.annotation.shared.AnnotationTagType;
import stroom.core.client.ContentManager;
import stroom.security.client.api.ClientSecurityContext;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class AnnotationStatusPlugin extends AnnotationPlugin {

    @Inject
    AnnotationStatusPlugin(final EventBus eventBus,
                           final ContentManager contentManager,
                           final Provider<AnnotationTagPresenter> presenterProvider,
                           final ClientSecurityContext securityContext) {
        super(eventBus,
                contentManager,
                presenterProvider,
                securityContext,
                "Annotation Statuses",
                AnnotationTagType.STATUS);
    }
}
