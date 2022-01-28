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
}
