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

import stroom.pipeline.refdata.store.offheapstore.OffHeapRefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.onheapstore.OnHeapRefDataValueProxyConsumer;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;

public class RefDataValueProxyConsumerFactory {

    private final Receiver receiver;
    private final PipelineConfiguration pipelineConfiguration;

    private final OnHeapRefDataValueProxyConsumer.Factory onHeapRefDataValueProxyConsumerFactory;
    private final OffHeapRefDataValueProxyConsumer.Factory offHeapRefDataValueProxyConsumerFactory;
    private OnHeapRefDataValueProxyConsumer onHeapRefDataValueProxyConsumer = null;
    private OffHeapRefDataValueProxyConsumer offHeapRefDataValueProxyConsumer = null;

    @Inject
    public RefDataValueProxyConsumerFactory(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final OnHeapRefDataValueProxyConsumer.Factory onHeapRefDataValueProxyConsumerFactory,
            final OffHeapRefDataValueProxyConsumer.Factory offHeapRefDataValueProxyConsumerFactory) {

        this.receiver = receiver;
        this.pipelineConfiguration = pipelineConfiguration;
        this.onHeapRefDataValueProxyConsumerFactory = onHeapRefDataValueProxyConsumerFactory;
        this.offHeapRefDataValueProxyConsumerFactory = offHeapRefDataValueProxyConsumerFactory;

    }

    public RefDataValueProxyConsumer getConsumer(final RefDataStore.StorageType storageType) {

        final RefDataValueProxyConsumer refDataValueProxyConsumer;
        if (storageType.equals(RefDataStore.StorageType.OFF_HEAP)) {
            if (offHeapRefDataValueProxyConsumer == null) {
                offHeapRefDataValueProxyConsumer = offHeapRefDataValueProxyConsumerFactory.create(
                        receiver, pipelineConfiguration);
            }
            refDataValueProxyConsumer = offHeapRefDataValueProxyConsumer;

        } else if (storageType.equals(RefDataStore.StorageType.ON_HEAP)) {
            if (onHeapRefDataValueProxyConsumer == null) {
                onHeapRefDataValueProxyConsumer = onHeapRefDataValueProxyConsumerFactory.create(
                        receiver, pipelineConfiguration);
            }
            refDataValueProxyConsumer = onHeapRefDataValueProxyConsumer;
        } else {
            throw new IllegalArgumentException(LogUtil.message("Unexpected type {}", storageType));
        }
        return refDataValueProxyConsumer;
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        RefDataValueProxyConsumerFactory create(final Receiver receiver,
                                                final PipelineConfiguration pipelineConfiguration);
    }
}
