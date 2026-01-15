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

package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A wrapper for multiple {@link RefDataValueProxy} objects. A lookup (consumeBytes or supplyValue)
 * will perform a lookup against each sub-proxy in turn until a value is found.
 * <p>
 * This is used when we have multiple ref loaders attached to an XSLT. Any one of them may be
 * able to provide the value. This means the order of ref loaders is important for performance.
 * The one most likely to yield results should be at the top.
 */
public class MultiRefDataValueProxy implements RefDataValueProxy {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MultiRefDataValueProxy.class);

    private final List<SingleRefDataValueProxy> refDataValueProxies;

    private MultiRefDataValueProxy(final List<SingleRefDataValueProxy> refDataValueProxies) {
        this.refDataValueProxies = Objects.requireNonNull(refDataValueProxies);

        if (refDataValueProxies.size() < 2) {
            throw new RuntimeException("Must provide at least two proxy values.");
        }
    }

    @Override
    public String getKey() {
        // Ctor ensure we have at least one proxy and all keys are the same.
        return refDataValueProxies.get(0)
                .getKey();
    }

    @Override
    public String getMapName() {
        // Ctor ensure we have at least one proxy and all map names are the same.
        return refDataValueProxies.get(0)
                .getMapName();
    }

    @Override
    public List<MapDefinition> getMapDefinitions() {
        return refDataValueProxies.stream()
                .flatMap(refDataValueProxy -> refDataValueProxy.getMapDefinitions().stream())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MapDefinition> getSuccessfulMapDefinition() {
        return refDataValueProxies.stream()
                .map(SingleRefDataValueProxy::getSuccessfulMapDefinition)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Merge the two proxies into one combined one that will use each contained proxy to try to get a value.
     * Argument order is CRITICAL to ensure lookups are done in the correct order.
     *
     * @param existingRefDataValueProxy The existing
     * @param newRefDataValueProxy
     * @return
     */
    public static MultiRefDataValueProxy merge(final RefDataValueProxy existingRefDataValueProxy,
                                               final RefDataValueProxy newRefDataValueProxy) {
        Objects.requireNonNull(existingRefDataValueProxy);
        Objects.requireNonNull(newRefDataValueProxy);

        if (!Objects.equals(existingRefDataValueProxy.getKey(), newRefDataValueProxy.getKey())) {
            throw new RuntimeException(LogUtil.message("All keys should be the same. Found '{}' and '{}'",
                    existingRefDataValueProxy.getKey(),
                    newRefDataValueProxy.getKey()));
        }

        if (!Objects.equals(existingRefDataValueProxy.getMapName(), newRefDataValueProxy.getMapName())) {
            throw new RuntimeException(LogUtil.message("All map names should be the same. Found '{}' and '{}'",
                    existingRefDataValueProxy.getMapName(),
                    newRefDataValueProxy.getMapName()));
        }

        // Order is CRITICAL here, so we attempt lookups in the right order
        final List<SingleRefDataValueProxy> existingProxies = getContainedProxies(existingRefDataValueProxy);
        final List<SingleRefDataValueProxy> newProxies = getContainedProxies(newRefDataValueProxy);
        final ArrayList<SingleRefDataValueProxy> combinedList = new ArrayList<>(
                existingProxies.size() + newProxies.size());

        // Order is CRITICAL here, so we attempt lookups in the right order
        combinedList.addAll(existingProxies);
        combinedList.addAll(newProxies);

        LOGGER.trace(() -> LogUtil.message("Merging {} proxies in this order\n{}",
                combinedList.size(),
                combinedList.stream()
                        .map(proxy -> "  " + proxy.toString())
                        .collect(Collectors.joining("\n"))));

        return new MultiRefDataValueProxy(Collections.unmodifiableList(combinedList));
    }

    @Override
    public RefDataValueProxy merge(final RefDataValueProxy additionalProxy) {
        return merge(this, additionalProxy);
    }

    private static List<SingleRefDataValueProxy> getContainedProxies(final RefDataValueProxy refDataValueProxy) {
        Objects.requireNonNull(refDataValueProxy);
        if (refDataValueProxy instanceof SingleRefDataValueProxy) {
            return List.of((SingleRefDataValueProxy) refDataValueProxy);
        } else if (refDataValueProxy instanceof MultiRefDataValueProxy) {
            return ((MultiRefDataValueProxy) refDataValueProxy).refDataValueProxies;
        } else {
            throw new RuntimeException(LogUtil.message("Unexpected type " + refDataValueProxy.getClass().getName()));
        }
    }

    @Override
    public Optional<RefDataValue> supplyValue() {
        // try each of our proxies in turn and as soon as one finds a result break out
        Optional<RefDataValue> optResult = Optional.empty();
        for (final RefDataValueProxy refDataValueProxy : refDataValueProxies) {

            LOGGER.trace("Attempting to supplyValue with sub-proxy {}", refDataValueProxy);
            optResult = refDataValueProxy.supplyValue();
            if (optResult.isPresent()) {
                LOGGER.trace("Found result with sub-proxy {}", refDataValueProxy);
                break;
            }
        }
        return optResult;
    }

    @Override
    public boolean consumeBytes(final Consumer<TypedByteBuffer> typedByteBufferConsumer) {
        // try each of our proxies in turn and as soon as one finds a result break out
        boolean foundValue = false;
        // We could construct this object with a pipeline scoped object to hold a
        // map of mapName to refDataValueProxy which we could populate when we find a
        // result. Thus for any future lookups we could try that one first before looping over
        // the rest.  For pipelines with a lot of ref loaders this should speed things up.
        // The downside of this is that it would change the behavior in the event that two
        // ref streams can supply a value for the same map/key
        for (final SingleRefDataValueProxy refDataValueProxy : refDataValueProxies) {
            LOGGER.trace("Attempting to consumeBytes with sub-proxy {}", refDataValueProxy);
            foundValue = refDataValueProxy.consumeBytes(typedByteBufferConsumer);
            if (foundValue) {
                LOGGER.trace("Found result with sub-proxy {}", refDataValueProxy);
                break;
            }
        }
        if (foundValue) {
            LOGGER.trace("Result found for proxy {}", this);
        } else {
            LOGGER.trace("No result found for proxy {}", this);
        }
        return foundValue;
    }

    @Override
    public boolean consumeValue(final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory) {
        // try each of our proxies in turn and as soon as one finds a result break out
        boolean foundValue = false;
        // We could construct this object with a pipeline scoped object to hold a
        // map of mapName to refDataValueProxy which we could populate when we find a
        // result. Thus for any future lookups we could try that one first before looping over
        // the rest.  For pipelines with a lot of ref loaders this should speed things up.
        // The downside of this is that it would change the behavior in the event that two
        // ref streams can supply a value for the same map/key
        for (final RefDataValueProxy refDataValueProxy : refDataValueProxies) {
            LOGGER.trace("Attempting to consumeBytes with sub-proxy {}", refDataValueProxy);
            foundValue = refDataValueProxy.consumeValue(refDataValueProxyConsumerFactory);
            if (foundValue) {
                LOGGER.trace("Found result with sub-proxy {}", refDataValueProxy);
                break;
            }
        }
        if (foundValue) {
            LOGGER.trace("Result found for proxy {}", this);
        } else {
            LOGGER.trace("No result found for proxy {}", this);
        }
        return foundValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MultiRefDataValueProxy that = (MultiRefDataValueProxy) o;
        return Objects.equals(refDataValueProxies, that.refDataValueProxies);
    }

    @Override
    public int hashCode() {

        return Objects.hash(refDataValueProxies);
    }

    @Override
    public String toString() {
        return "MultiRefDataValueProxy{" +
                "refDataValueProxies=" + refDataValueProxies +
                '}';
    }
}
