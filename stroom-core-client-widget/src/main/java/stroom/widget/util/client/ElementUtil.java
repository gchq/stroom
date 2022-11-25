package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;

public class ElementUtil {

    public static boolean hasClassName(final Element element,
                                       final String className) {
        return hasClassName(element, className, 0, 0);
    }

    public static boolean hasClassName(final Element element,
                                       final String className,
                                       final int depth,
                                       final int maxDepth) {
        if (element == null) {
            return false;
        }

        /*
         * DON'T CHANGE THIS CODE.
         *
         * Although it appears that `element.getClassName()` returns a String it actually returns an SVGAnimatedString
         * object for SVG elements when it is running as JavaScript. See here:
         * https://developer.mozilla.org/en-US/docs/Web/API/Element/className
         *
         * To avoid this problem we get the class attribute of the element instead.
         */
        final String elementClassName = element.getAttribute("class");
        if (elementClassName.contains(className)) {
            return true;
        }

        if (depth < maxDepth) {
            return hasClassName(element.getParentElement(), className, depth + 1, maxDepth);
        }

        return false;
    }

    public static Rect getClientRect(Element el) {
        return new Rect(getClientTop(el), getClientBottom(el), getClientLeft(el), getClientRight(el));
    }

    public static Rect getInnerClientRect(Element el) {
        final int top = getClientTop(el);
        final int bottom = top + el.getClientHeight();
        final int left = getClientLeft(el);
        final int right = left + el.getClientWidth();
        return new Rect(top, bottom, left, right);
    }

    public static native int getClientLeft(Element el) /*-{
     return window.pageXOffset + el.getBoundingClientRect().left;
    }-*/;

    public static native int getClientRight(Element el) /*-{
     return window.pageXOffset + el.getBoundingClientRect().right;
    }-*/;

    public static native int getClientTop(Element el) /*-{
     return window.pageYOffset + el.getBoundingClientRect().top;
    }-*/;

    public static native int getClientBottom(Element el) /*-{
     return window.pageYOffset + el.getBoundingClientRect().bottom;
    }-*/;
}
