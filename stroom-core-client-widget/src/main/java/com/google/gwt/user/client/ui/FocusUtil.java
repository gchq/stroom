package com.google.gwt.user.client.ui;

import stroom.widget.util.client.ElementUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;

public class FocusUtil {

    public static void forceFocus(final Runnable runnable) {
        for (int i = 0; i <= 500; i += 100) {
            final Timer timer = new Timer() {
                @Override
                public void run() {
                    runnable.run();
                }
            };
            timer.schedule(i);
        }
    }

    public static void focusRow(final Element elem) {
        // Make sure the selected element is the row as we select the whole row.
        Element tr = elem;
        while (tr != null && !tr.getTagName().equalsIgnoreCase("tr")) {
            tr = tr.getParentElement();
        }
        if (tr != null) {
            ElementUtil.scrollIntoViewNearest(tr);
        }

        ElementUtil.focus(elem, false, true);
    }
}
