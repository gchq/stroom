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

package stroom.core.receive;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.query.api.datasource.QueryField;
import stroom.receive.content.shared.ContentTemplateResource;
import stroom.receive.content.shared.ContentTemplates;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Set;

@AutoLogged
public class ContentTemplateResourceImpl implements ContentTemplateResource {

    private final Provider<ContentTemplateService> contentTemplateServiceProvider;

    @Inject
    public ContentTemplateResourceImpl(final Provider<ContentTemplateService> contentTemplateServiceProvider) {
        this.contentTemplateServiceProvider = contentTemplateServiceProvider;
    }

    @Override
    public ContentTemplates fetch() {
        return contentTemplateServiceProvider.get().fetch();
    }

    @Override
    public ContentTemplates update(final ContentTemplates contentTemplates) {
        return contentTemplateServiceProvider.get().update(contentTemplates);
    }

    @Override
    public Set<QueryField> fetchFields() {
        return contentTemplateServiceProvider.get().fetchFields();
    }
}
