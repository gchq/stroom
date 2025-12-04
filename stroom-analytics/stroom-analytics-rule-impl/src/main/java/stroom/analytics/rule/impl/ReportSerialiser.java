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

import stroom.analytics.shared.ReportDoc;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class ReportSerialiser implements DocumentSerialiser2<ReportDoc> {

    private final Serialiser2<ReportDoc> delegate;

    @Inject
    public ReportSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(ReportDoc.class);
    }

    @Override
    public ReportDoc read(final Map<String, byte[]> data) throws IOException {
        final ReportDoc document = delegate.read(data);
        return document;
    }

    @Override
    public Map<String, byte[]> write(final ReportDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);
        return data;
    }
}
