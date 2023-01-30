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

package stroom.query.common.v2;

import stroom.query.api.v2.ResultStoreSettings;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ResultStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStore.class);

    private final ConcurrentHashMap<String, Set<Throwable>> errors = new ConcurrentHashMap<>();
    private final List<String> highlights;
    private final Coprocessors coprocessors;
    private final String userId;
    private final Instant creationTime;
    private volatile Instant lastAccessTime;
    private final String nodeName;

    private final SearchResponseCreator searchResponseCreator;
    private final ResultStoreSettings resultStoreSettings;
    private volatile SearchProcess searchProcess;
    private volatile boolean terminate;

    public ResultStore(final SerialisersFactory serialisersFactory,
                       final SizesProvider sizesProvider,
                       final String userId,
                       final List<String> highlights,
                       final Coprocessors coprocessors,
                       final String nodeName,
                       final ResultStoreSettings resultStoreSettings) {
        this.highlights = Optional
                .ofNullable(highlights)
                .map(Collections::unmodifiableList)
                .orElse(Collections.emptyList());
        this.coprocessors = coprocessors;
        this.userId = userId;
        this.creationTime = Instant.now();
        lastAccessTime = creationTime;
        this.nodeName = nodeName;
        this.resultStoreSettings = resultStoreSettings;

        searchResponseCreator = new SearchResponseCreator(serialisersFactory, sizesProvider, this);
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
        LOGGER.trace(() -> "terminate()", new RuntimeException("terminate"));
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

        LOGGER.trace(() -> "destroy()", new RuntimeException("destroy"));
        LOGGER.trace(() -> "coprocessors.clear()");
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
            final Set<Throwable> errors = new HashSet<>();
            final Consumer<Throwable> errorConsumer = (error) -> {
                LOGGER.debug(error::getMessage, error);
                if (!ErrorConsumerUtil.isInterruption(error)) {
                    errors.add(error);
                }
            };

            complete = NodeResultSerialiser.read(input, coprocessors, errorConsumer);
            addErrors(nodeName, errors);
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

    public synchronized void onFailure(final String nodeName,
                                       final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        if (!ErrorConsumerUtil.isInterruption(throwable)) {
            addErrors(nodeName, Collections.singleton(throwable));
        }
    }

    public void addError(final Throwable error) {
        final Set<Throwable> errorSet = errors.computeIfAbsent("local", k ->
                Collections.newSetFromMap(new ConcurrentHashMap<>()));
        LOGGER.debug(error::getMessage, error);
        errorSet.add(error);
    }

    private void addErrors(final String nodeName,
                           final Set<Throwable> newErrors) {
        if (newErrors != null && newErrors.size() > 0) {
            final Set<Throwable> errorSet = errors.computeIfAbsent(nodeName, k ->
                    Collections.newSetFromMap(new ConcurrentHashMap<>()));
            for (final Throwable error : newErrors) {
                LOGGER.debug(error::getMessage, error);
                errorSet.add(error);
            }
        }
    }

    public List<String> getErrors() {
        if (errors.size() == 0 && !coprocessors.getErrorConsumer().hasErrors()) {
            return Collections.emptyList();
        }

        final List<String> err = new ArrayList<>();
        for (final Entry<String, Set<Throwable>> entry : errors.entrySet()) {
            final String nodeName = entry.getKey();
            final Set<Throwable> errors = entry.getValue();

            if (errors.size() > 0) {
                err.add("Node: " + nodeName);

                for (final Throwable error : errors) {
                    err.add("\t" + ExceptionStringUtil.getMessage(error));
                }
            }
        }
        // Add any errors from the coprocessors
        err.addAll(coprocessors.getErrorConsumer().getErrors());

        return err;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public DataStore getData(final String componentId) {
        return coprocessors.getData(componentId);
    }

    public String getUserId() {
        return userId;
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

    public SearchResponse search(final SearchRequest request) {
        final SearchResponse response = searchResponseCreator.create(request);
        lastAccessTime = Instant.now();
        return response;
    }

    @Override
    public String toString() {
        return "StoreImpl{" +
                ", complete=" + coprocessors.getCompletionState() +
                '}';
    }
}
