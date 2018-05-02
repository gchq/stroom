package stroom.refdata.saxevents;

import net.sf.saxon.expr.XPathContext;
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

    public static EventListProxyConsumer getConsumer(final ValueProxy<EventListValue> eventListProxy,
                                                     final XPathContext context) {

        final Class clazz = eventListProxy.getValueClazz();
        if (clazz == FastInfosetValue.class) {
            return new FastInfosetConsumer(context);
        } else if (clazz == StringValue.class) {
            return new StringValueConsumer(context);
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", clazz.getCanonicalName()));
        }
    }

}
