package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;

public class ElementUtil {

    public static boolean hasClassName(final Element element,
                                       final String className) {
        return findMatching(element, className, 0, 0) != null;
    }

    public static boolean hasClassName(final Element element,
                                       final String className,
                                       final int depth,
                                       final int maxDepth) {
        return findMatching(element, className, depth, maxDepth) != null;
    }

    public static Element findMatching(final Element element,
                                       final String className) {
        return findMatching(element, className, 0, 0);
    }

    public static Element findMatching(final Element element,
                                       final String className,
                                       final int depth,
                                       final int maxDepth) {
        if (element == null) {
            return null;
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
            return element;
        }

        if (depth < maxDepth) {
            return findMatching(element.getParentElement(), className, depth + 1, maxDepth);
        }

        return null;
    }

    public static Rect getClientRect(Element el) {
        final double windowScrollY = getWindowScrollY();
        final double windowScrollX = getWindowScrollX();
        final double top = windowScrollY + getBoundingClientRectTop(el);
        final double bottom = top + getSubPixelClientHeight(el);
        final double left = windowScrollX + getBoundingClientRectLeft(el);
        final double right = left + getSubPixelClientWidth(el);
        return new Rect(top, bottom, left, right);
    }

    public static Rect getBoundingClientRectPlusWindowScroll(final Element el) {
        final double windowScrollY = getWindowScrollY();
        final double windowScrollX = getWindowScrollX();
        final double top = windowScrollY + getBoundingClientRectTop(el);
        final double bottom = windowScrollY + getBoundingClientRectBottom(el);
        final double left = windowScrollX + getBoundingClientRectLeft(el);
        final double right = windowScrollX + getBoundingClientRectRight(el);
        return new Rect(top, bottom, left, right);
    }

    public static Rect getBoundingClientRect(final Element el) {
        final double left = getBoundingClientRectLeft(el);
        final double right = getBoundingClientRectRight(el);
        final double top = getBoundingClientRectTop(el);
        final double bottom = getBoundingClientRectBottom(el);
        return new Rect(top,
                bottom,
                left,
                right);
    }

    public static double getClientLeft(Element el) {
        return getWindowScrollX() + getBoundingClientRectLeft(el);
    }

    public static double getClientRight(Element el) {
        return getWindowScrollX() + getBoundingClientRectRight(el);
    }

    public static double getClientTop(Element el) {
        return getWindowScrollY() + getBoundingClientRectTop(el);
    }

    public static double getClientBottom(Element el) {
        return getWindowScrollY() + getBoundingClientRectBottom(el);
    }

    public static native double getBoundingClientRectLeft(Element el) /*-{
        return el.getBoundingClientRect().left || 0;
    }-*/;

    public static native double getBoundingClientRectRight(Element el) /*-{
        return el.getBoundingClientRect().right || 0;
    }-*/;

    public static native double getBoundingClientRectTop(Element el) /*-{
        return el.getBoundingClientRect().top || 0;
    }-*/;

    public static native double getBoundingClientRectBottom(Element el) /*-{
        return el.getBoundingClientRect().bottom || 0;
    }-*/;

    public static native double getWindowScrollX() /*-{
        return window.scrollX || 0;
    }-*/;

    public static native double getWindowScrollY() /*-{
        return window.scrollY || 0;
    }-*/;

    public static native double getSubPixelClientHeight(Element el) /*-{
        return el.clientHeight || 0;
    }-*/;

    public static native double getSubPixelClientWidth(Element el) /*-{
        return el.clientWidth || 0;
    }-*/;

    public static native double getSubPixelOffsetHeight(Element el) /*-{
        return el.offsetHeight || 0;
    }-*/;

    public static native double getSubPixelOffsetLeft(Element el) /*-{
        return el.offsetLeft || 0;
    }-*/;

    public static native double getSubPixelOffsetTop(Element el) /*-{
        return el.offsetTop || 0;
    }-*/;

    public static native double getSubPixelOffsetWidth(Element el) /*-{
        return el.offsetWidth || 0;
    }-*/;

    public static native double getSubPixelScrollHeight(Element el) /*-{
        return el.scrollHeight || 0;
    }-*/;

    public static native double getSubPixelScrollTop(Element el) /*-{
        return el.scrollTop || 0;
    }-*/;

    public static native double getSubPixelScrollWidth(Element el) /*-{
        return el.scrollWidth || 0;
    }-*/;

    public static native void focus(Element el) /*-{
        el.focus();
    }-*/;

    public static native void focus(Element el, boolean focusVisible, boolean preventScroll) /*-{
        el.focus({ focusVisible: focusVisible, preventScroll: preventScroll });
    }-*/;

    public static native void scrollIntoView(Element el, boolean alignToTop) /*-{
        el.scrollIntoView(alignToTop);
    }-*/;

    public static native void scrollIntoViewNearest(Element el) /*-{
        el.scrollIntoView({behaviour: "auto", block: "start", inline: "nearest"});
    }-*/;

    public static native void scrollIntoViewVertical(Element elem) /*-{
        var top = elem.offsetTop;
        var height = elem.offsetHeight;

        if (elem.parentNode != elem.offsetParent) {
            top -= elem.parentNode.offsetTop;
        }

        var cur = elem.parentNode;
        while (cur && (cur.nodeType == 1)) {
            if (top < cur.scrollTop) {
                cur.scrollTop = top;
            }
            if (top + height > cur.scrollTop + cur.clientHeight) {
                cur.scrollTop = (top + height) - cur.clientHeight;
            }

            var offsetTop = cur.offsetTop;
            if (cur.parentNode != cur.offsetParent) {
                offsetTop -= cur.parentNode.offsetTop;
            }

            top += offsetTop - cur.scrollTop;
            cur = cur.parentNode;
        }
    }-*/;

    public static native void scrollIntoViewHorizontal(Element elem) /*-{
        var left = elem.offsetLeft;
        var width = elem.offsetWidth;

        if (elem.parentNode != elem.offsetParent) {
            left -= elem.parentNode.offsetLeft;
        }

        var cur = elem.parentNode;
        while (cur && (cur.nodeType == 1)) {
            if (left < cur.scrollLeft) {
                cur.scrollLeft = left;
            }
            if (left + width > cur.scrollLeft + cur.clientWidth) {
                cur.scrollLeft = (left + width) - cur.clientWidth;
            }

            var offsetLeft = cur.offsetLeft;
            if (cur.parentNode != cur.offsetParent) {
                offsetLeft -= cur.parentNode.offsetLeft;
            }

            left += offsetLeft - cur.scrollLeft;
            cur = cur.parentNode;
        }
    }-*/;
}
