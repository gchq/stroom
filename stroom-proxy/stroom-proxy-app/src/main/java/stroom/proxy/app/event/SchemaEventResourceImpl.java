/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.app.event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

@Singleton
public class SchemaEventResourceImpl implements SchemaEventResource {

    private final Provider<EventAppenders> eventAppendersProvider;

    private final ReceiveDataHelper receiveDataHelper;


    @Inject
    public SchemaEventResourceImpl(final Provider<EventAppenders> eventAppendersProvider,
                                   final ReceiveDataHelper receiveDataHelper) {
        this.eventAppendersProvider = eventAppendersProvider;
        this.receiveDataHelper = receiveDataHelper;
    }

    @Override
    public String schema_v4_0(final HttpServletRequest request,
                              final String event) {
        // TODO : Validate event text against schema.

        return receiveDataHelper.process(request, attributeMap -> {
            eventAppendersProvider.get().consume(attributeMap, outputStream -> {
                try {
                    outputStream.write(event.getBytes(StandardCharsets.UTF_8));

                    // TODO : Add a different delimiter if needed, e.g. a comma for JSON.
                    outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        });
    }
}
