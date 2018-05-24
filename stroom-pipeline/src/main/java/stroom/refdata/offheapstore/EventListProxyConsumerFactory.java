/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import net.sf.saxon.expr.XPathContext;
import stroom.refdata.saxevents.FastInfosetValue;
import stroom.refdata.saxevents.StringValue;
import stroom.util.logging.LambdaLogger;

public class EventListProxyConsumerFactory {

//    public static EventListProxyConsumer getConsumer(final ValueProxy<EventListValue> eventListProxy,
//                                                     final Receiver receiver, final PipelineConfiguration pipe) {
//
//        return getConsumerSupplier(eventListProxy)
//                .apply(receiver, pipe);
//    }

//    public static BiFunction<Receiver, PipelineConfiguration, EventListProxyConsumer> getConsumerSupplier(
//            final ValueProxy<EventListValue> eventListProxy) {
//
//        final Class clazz = eventListProxy.getValueClazz();
//        if (clazz == FastInfosetValue.class) {
//            return FastInfosetConsumer::new;
//        } else if (clazz == StringValue.class) {
//            return StringValueConsumer::new;
//        } else {
//            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", clazz.getCanonicalName()));
//        }
//    }

    public static EventListProxyConsumer getConsumer(final RefDataValueProxy refDataValueProxy,
                                                     final XPathContext context) {

        final Class<? extends RefDataValue> valueClass = refDataValueProxy.getValueClass();
        if (valueClass.isAssignableFrom(FastInfosetValue.class)) {
            return new FastInfosetConsumer(context);
        } else if (valueClass.isAssignableFrom(StringValue.class)) {
            return new StringValueConsumer(context);
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", valueClass.getCanonicalName()));
        }
    }

}
