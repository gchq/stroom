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
         * Although it appears that `element.getClassName()` returns a String it actually doesn't when it is running as
         * JavaScript and therefore must be tested and cast to a String.
         */
        final Object elementClassName = element.getClassName();
        if (elementClassName instanceof String) {
            if (((String) elementClassName).contains(className)) {
                return true;
            }
        }

        if (depth < maxDepth) {
            return hasClassName(element.getParentElement(), className, depth + 1, maxDepth);
        }

        return false;
    }
}
