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

package stroom.query.common.v2;

import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchResponse;
import stroom.query.api.SearchTaskProgress;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.util.shared.UserRef;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ResultStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStore.class);

    private final ConcurrentHashMap<String, ErrorConsumer> errors = new ConcurrentHashMap<>();
    private final SearchRequestSource searchRequestSource;
    private final Set<String> highlights = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final CoprocessorsImpl coprocessors;
    private final UserRef userRef;
    private final Instant creationTime;
    private volatile Instant lastAccessTime;
    private final String nodeName;

    private final SearchResponseCreator searchResponseCreator;
    private volatile ResultStoreSettings resultStoreSettings;
    private volatile SearchProcess searchProcess;
    private volatile boolean terminate;

    public ResultStore(final SearchRequestSource searchRequestSource,
                       final SizesProvider sizesProvider,
                       final UserRef userRef,
                       final CoprocessorsImpl coprocessors,
                       final String nodeName,
                       final ResultStoreSettings resultStoreSettings,
                       final MapDataStoreFactory mapDataStoreFactory,
                       final ExpressionPredicateFactory expressionPredicateFactory) {
        this.searchRequestSource = searchRequestSource;
        this.coprocessors = coprocessors;
        this.userRef = userRef;
        this.creationTime = Instant.now();
        lastAccessTime = creationTime;
        this.nodeName = nodeName;
        this.resultStoreSettings = resultStoreSettings;
        searchResponseCreator = new SearchResponseCreator(
                sizesProvider,
                this,
                coprocessors.getExpressionContext(),
                mapDataStoreFactory,
                expressionPredicateFactory);
    }

    public Map<String, ResultCreator> makeDefaultResultCreators(final SearchRequest searchRequest) {
        return searchResponseCreator.makeDefaultResultCreators(searchRequest);
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public Coprocessors getCoprocessors() {
        return coprocessors;
    }

    public synchronized void setSearchProcess(final SearchProcess searchProcess) {
        this.searchProcess = searchProcess;
        if (terminate) {
            searchProcess.onTerminate();
        }
    }

    public synchronized void terminate() {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(() -> "terminate()", new RuntimeException("terminate"));
        }

        this.terminate = true;
        if (searchProcess != null) {
            searchProcess.onTerminate();
        }
    }

    /**
     * Stop searching and destroy any stored data.
     */
    public synchronized void destroy() {
        terminate();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(() -> "destroy()", new RuntimeException("destroy"));
            LOGGER.trace(() -> "coprocessors.clear()");
        }

        coprocessors.clear();
    }

    public void signalComplete() {
        LOGGER.trace(() -> "complete()");
        coprocessors.getCompletionState().signalComplete();
    }

    public boolean isComplete() {
        return coprocessors.getCompletionState().isComplete();
    }

    public void awaitCompletion() throws InterruptedException {
        coprocessors.getCompletionState().awaitCompletion();
    }

    public boolean awaitCompletion(final long timeout,
                                   final TimeUnit unit) throws InterruptedException {
        return coprocessors.getCompletionState().awaitCompletion(timeout, unit);
    }

    public synchronized boolean onSuccess(final String nodeName,
                                          final InputStream inputStream) {
        // If we have already completed the finish immediately without worrying about this data.
        if (isComplete()) {
            return true;
        }
        boolean complete = true;

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamUtil.streamToStream(inputStream, byteArrayOutputStream);

        try (final Input input = new Input(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            final ErrorConsumer errorConsumer = getErrorConsumer(nodeName);
            complete = NodeResultSerialiser.read(input, coprocessors, errorConsumer);
        } catch (final KryoException e) {
            // Expected as sometimes the output stream is closed by the receiving node.
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            onFailure(nodeName, e);
        }

        // If the result collector rejected the result it is because we have already collected enough data and can
        // therefore consider search complete.
        return isComplete() || complete;
    }

    private ErrorConsumer getErrorConsumer(final String nodeName) {
        return errors.computeIfAbsent(nodeName, k -> new ErrorConsumerImpl());
    }

    public synchronized void onFailure(final String nodeName,
                                       final Throwable throwable) {
        final ErrorConsumer errorConsumer = getErrorConsumer(nodeName);
        errorConsumer.add(throwable);
    }

    public void addError(final Throwable error) {
        final ErrorConsumer errorConsumer = getErrorConsumer(nodeName);
        errorConsumer.add(error);
    }

    public List<ErrorMessage> getErrors() {
        if (errors.isEmpty() && !coprocessors.getErrorConsumer().hasErrors()) {
            return Collections.emptyList();
        }

        final List<ErrorMessage> err = new ArrayList<>();
        for (final Entry<String, ErrorConsumer> entry : errors.entrySet()) {
            final String nodeName = entry.getKey();
            final ErrorConsumer errorConsumer = entry.getValue();
            final List<ErrorMessage> errors = errorConsumer.getErrorMessages();

            if (!errors.isEmpty()) {
                err.add(new ErrorMessage(Severity.ERROR, "Node: " + nodeName));
                for (final ErrorMessage error : errors) {
                    err.add(new ErrorMessage(error.getSeverity(), "\t" + error.getMessage()));
                }
            }
        }
        // Add any errors from the coprocessors
        err.addAll(coprocessors.getErrorConsumer().getErrorMessages());

        return err;
    }

    public List<String> getHighlights() {
        return List.copyOf(highlights);
    }

    public DataStore getData(final String componentId) {
        return coprocessors.getData(componentId);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    public String getNodeName() {
        return nodeName;
    }

    public ResultStoreSettings getResultStoreSettings() {
        return resultStoreSettings;
    }

    public SearchTaskProgress getSearchTaskProgress() {
        final SearchProcess searchProcess = this.searchProcess;
        if (searchProcess != null) {
            return searchProcess.getSearchTaskProgress();
        }
        return null;
    }

    public SearchResponse search(final SearchRequest request,
                                 final Map<String, ResultCreator> resultCreatorMap) {
        final SearchResponse response = searchResponseCreator.create(request, resultCreatorMap);
        lastAccessTime = Instant.now();
        return response;
    }

    public void setResultStoreSettings(final ResultStoreSettings resultStoreSettings) {
        this.resultStoreSettings = resultStoreSettings;
    }

    @Override
    public String toString() {
        return "StoreImpl{" +
               ", complete=" + coprocessors.getCompletionState() +
               '}';
    }

    public void addHighlights(final Set<String> highlights) {
        this.highlights.addAll(highlights);
    }
}
