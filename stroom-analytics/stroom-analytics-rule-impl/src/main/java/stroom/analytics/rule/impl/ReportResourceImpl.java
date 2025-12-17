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

package stroom.analytics.rule.impl;

import stroom.analytics.api.AnalyticsService;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportResource;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.string.StringWrapper;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class ReportResourceImpl implements ReportResource {

    private final Provider<ReportStore> reportStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<AnalyticsService> analyticsServiceProvider;

    @Inject
    ReportResourceImpl(final Provider<ReportStore> reportStoreProvider,
                       final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                       final Provider<AnalyticsService> analyticsServiceProvider) {
        this.reportStoreProvider = reportStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.analyticsServiceProvider = analyticsServiceProvider;
    }

    @Override
    public ReportDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(reportStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ReportDoc update(final String uuid, final ReportDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(reportStoreProvider.get(), doc);
    }

    @AutoLogged(OperationType.UNLOGGED) // Just a dry run
    @Override
    public StringWrapper testTemplate(final StringWrapper template) {
        return StringWrapper.wrap(analyticsServiceProvider.get().testTemplate(template.getString()));
    }

    @AutoLogged(OperationType.UNLOGGED) // Just a dry run
    @Override
    public void sendTestEmail(final NotificationEmailDestination emailDestination) {
        analyticsServiceProvider.get().sendTestEmail(emailDestination);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ReportDoc.TYPE)
                .build();
    }
}
