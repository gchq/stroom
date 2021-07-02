package stroom.widget.tab.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;

import java.util.function.Consumer;

public class ResizeObserver {

//    private final Consumer<Element> consumer;
//
//    public ResizeObserver(final Consumer<Element> consumer) {
//        this.consumer = consumer;
//    }
//
//    private void onResize(Element e) {
//        consumer.accept(e);
////        GWT.log(e.toString());
//    }

    public static native void observe(final Element element, final ResizeListener listener) /*-{
        var resizeObserver = new ResizeObserver(function(entries) {
            entries.forEach(function(entry) {


                listener.@stroom.widget.tab.client.view.ResizeListener::onResize(*)(entry.target);

//                handler(entry.target);
                //console.log(entry.target.id + ': ' + JSON.stringify(entry.contentRect));
            });
        });
        resizeObserver.observe(element)
    }-*/;
}
