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

package stroom.pipeline.writer;

import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Strings;
import jakarta.inject.Inject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@ConfigurableElement(
        type = "DictionaryAppender",
        category = Category.DESTINATION,
        description = """
                A destination used to write the output stream to a dictionary overwriting all previous data.""",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.DOCUMENT_DICTIONARY)
public class DictionaryAppender extends AbstractAppender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DictionaryAppender.class);

    private final DictionaryStore dictionaryStore;
    private final DocRefInfoService docRefInfoService;

    private DocRef dictionaryRef;

    private ByteArrayOutputStream byteArrayOutputStream;
    private BufferedOutputStream bufferedOutputStream;

    @Inject
    public DictionaryAppender(final ErrorReceiverProxy errorReceiverProxy,
                              final DictionaryStore dictionaryStore,
                              final DocRefInfoService docRefInfoService) {
        super(errorReceiverProxy);
        this.dictionaryStore = dictionaryStore;
        this.docRefInfoService = docRefInfoService;
    }

    @Override
    protected Output createOutput() {
        if (dictionaryRef == null) {
            fatal("Dictionary not set");
        }

        String dictionaryName = null;
        dictionaryName = docRefInfoService.name(dictionaryRef).orElse(null);
        if (Strings.isNullOrEmpty(dictionaryName)) {
            fatal("Dictionary not found: " + dictionaryRef);
        }

        byteArrayOutputStream = new ByteArrayOutputStream();
        bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream);

        final WrappedOutputStream wrappedOutputStream = new WrappedOutputStream(bufferedOutputStream) {
            @Override
            public void close() throws IOException {
                super.flush();
                super.close();
                DictionaryAppender.this.close();
            }
        };

        return new BasicOutput(wrappedOutputStream);
    }

    private void close() {
        // Only do something if an output stream was used.
        if (bufferedOutputStream != null) {
            boolean success = false;
            RuntimeException lastException = null;
            final String data = byteArrayOutputStream.toString();

            // We will try this a few times as the dictionary could be updated simultaneously by another process and
            // fail.
            for (int i = 0; i < 100 && !success; i++) {
                try {
                    // See if the task has been terminated.
                    checkTermination();
                } catch (final RuntimeException e) {
                    // Log the error.
                    fatal("Terminated");
                }

                try {
                    DictionaryDoc dictionaryDoc = dictionaryStore.readDocument(dictionaryRef);
                    if (dictionaryDoc == null) {
                        throw new DocumentNotFoundException(dictionaryRef);
                    } else {
                        dictionaryDoc = dictionaryDoc.copy().data(data).build();
                        dictionaryStore.writeDocument(dictionaryDoc);
                        success = true;
                    }
                } catch (final DocumentNotFoundException e) {
                    fatal("Dictionary not found on load: " + dictionaryRef);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    lastException = e;
                }
            }

            if (!success && lastException != null) {
                error(lastException.getMessage(), lastException);
            }
        }
    }

    @PipelinePropertyDocRef(types = DictionaryDoc.TYPE)
    @PipelineProperty(
            description = "The dictionary that output text should be written to.",
            displayPriority = 1)
    public void setDictionary(final DocRef dictionaryRef) {
        this.dictionaryRef = dictionaryRef;
    }
}
