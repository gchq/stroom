/*
 * Copyright 2016 Crown Copyright
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

package stroom.editor.client.view;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Composite;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;

import java.util.List;

public class Editor extends Composite {
    private String text = "";
    private boolean textDirty;

    private int firstLineNumber = 1;
    private boolean firstLineNumberDirty;

    private List<Annotation> annotations;
    private boolean annotationsDirty;

    private List<Marker> markers;
    private boolean markersDirty;

    private boolean readOnly;
    private boolean readOnlyDirty;

    private AceEditorMode mode = AceEditorMode.XML;
    private boolean modeDirty = true;

    private AceEditorTheme theme = AceEditorTheme.CHROME;
    private boolean themeDirty = true;

    private boolean showGutter = true;
    private boolean showGutterDirty;

    private int gotoLine;
    private boolean gotoLineDirty;

    private Rect scrollMargin;
    private boolean scrollMarginDirty;

    private boolean useWrapMode;
    private boolean useWrapModeDirty;

    private boolean started;

    private final AceEditor editor;

    public Editor() {
        editor = new AceEditor();
        editor.addAttachHandler(event -> {
            if (event.isAttached()) {
                if (!started) {
                    editor.startEditor();
                    editor.setUseSoftTabs(true);
                    editor.setTabSize(2);
                    started = true;
                }
                updateText();
                updateFirstLineNumber();
                updateAnnotations();
                updateMarkers();
                updateReadOnly();
                updateMode();
                updateTheme();
                updateShowGutter();
                updateGotoLine();
                updateScrollMargin();
                updateUseWrapMode();
            }
        });

        editor.getElement().setClassName("editor");
        initWidget(editor);
    }

    public String getText() {
        if (editor.isAttached()) {
            return editor.getText();
        }

        return text;
    }

    public void setText(final String text) {
        textDirty = true;
        if (text == null) {
            this.text = "";
        } else {
            this.text = text;
        }
        updateText();

        gotoLineDirty = false;
    }

    private void updateText() {
        if (editor.isAttached() && textDirty) {
            editor.setText(this.text);
            textDirty = false;
        }
    }

    public void setFirstLineNumber(final int firstLineNumber) {
        firstLineNumberDirty = true;
        this.firstLineNumber = firstLineNumber;
        updateFirstLineNumber();
    }

    private void updateFirstLineNumber() {
        if (editor.isAttached() && firstLineNumberDirty) {
            editor.setFirstLineNumber(firstLineNumber);
            firstLineNumberDirty = false;
        }
    }

    public void setAnnotations(final List<Annotation> annotations) {
        annotationsDirty = true;
        this.annotations = annotations;
        updateAnnotations();
    }

    private void updateAnnotations() {
        if (editor.isAttached() && annotationsDirty) {
            editor.clearAnnotations();
            if (annotations != null) {
                for (final Annotation annotation : annotations) {
                    editor.addAnnotation(annotation.getRow(), annotation.getColumn(), annotation.getText(), annotation.getType());
                }
                editor.setAnnotations();
            }
            annotationsDirty = false;
        }
    }

    public void setMarkers(final List<Marker> markers) {
        markersDirty = true;
        this.markers = markers;
        updateMarkers();
    }

    private void updateMarkers() {
        if (editor.isAttached() && markersDirty) {
            editor.removeAllMarkers();
            if (markers != null) {
                for (final Marker marker : markers) {
                    editor.addMarker(marker.getRange(), marker.getClazz(), marker.getType(), marker.isInFront());
                }
            }
            markersDirty = false;
        }
    }

    public void setReadOnly(final boolean readOnly) {
        readOnlyDirty = true;
        this.readOnly = readOnly;
        updateReadOnly();
    }

    private void updateReadOnly() {
        if (editor.isAttached() && readOnlyDirty) {
            editor.setReadOnly(readOnly);
            readOnlyDirty = false;
        }
    }

    public void setMode(final AceEditorMode mode) {
        modeDirty = true;
        this.mode = mode;
        updateMode();
    }

    private void updateMode() {
        if (editor.isAttached() && modeDirty) {
            if (mode != null) {
                try {
                    editor.setMode(mode);
                } catch (final Exception e) {
                    throw new RuntimeException("Unable to set mode '" + mode.getName() + "' perhaps the javascript for this mode is missing");
                }
            }
            modeDirty = false;
        }
    }

    public void setTheme(final AceEditorTheme theme) {
        themeDirty = true;
        this.theme = theme;
        updateTheme();
    }

    private void updateTheme() {
        if (editor.isAttached() && themeDirty) {
            if (theme != null) {
                try {
                    editor.setTheme(theme);
                } catch (final Exception e) {
                    throw new RuntimeException("Unable to set theme '" + theme.getName() + "' perhaps the javascript for this theme is missing");
                }
            }
            themeDirty = false;
        }
    }

    public void setShowGutter(final boolean showGutter) {
        showGutterDirty = true;
        this.showGutter = showGutter;
        updateShowGutter();
    }

    private void updateShowGutter() {
        if (editor.isAttached() && showGutterDirty) {
            editor.setShowGutter(showGutter);
            showGutterDirty = false;
        }
    }

    public void gotoLine(final int line) {
        gotoLineDirty = true;
        this.gotoLine = line;
        updateGotoLine();
    }

    private void updateGotoLine() {
        if (editor.isAttached() && gotoLineDirty) {
            editor.gotoLine(gotoLine);
            gotoLineDirty = false;
        }
    }

    public int getLineCount() {
        if (editor.isAttached()) {
            return editor.getLineCount();
        }
        return 0;
    }

    public void setScrollMargin(final int top, final int bottom, final int left, final int right) {
        this.scrollMarginDirty = true;
        scrollMargin = new Rect(top, bottom, left, right);
        updateScrollMargin();
    }

    private void updateScrollMargin() {
        if (editor.isAttached() && scrollMarginDirty) {
            editor.setScrollMargin(scrollMargin.top, scrollMargin.bottom, scrollMargin.left, scrollMargin.right);
            scrollMarginDirty = false;
        }
    }

    public void setUseWrapMode(final boolean useWrapMode) {
        useWrapModeDirty = true;
        this.useWrapMode = useWrapMode;
        updateUseWrapMode();
    }

    private void updateUseWrapMode() {
        if (editor.isAttached() && useWrapModeDirty) {
            editor.setUseWrapMode(useWrapMode);
            useWrapModeDirty = false;
        }
    }

    public void onResize() {
        Scheduler.get().scheduleDeferred(() -> {
            if (editor.isAttached()) {
                editor.onResize();
            }
        });
    }

    public void beautify() {
        if (editor.isAttached()) {
            editor.beautify();
        }
    }

    private static class Rect {
        private final int top;
        private final int bottom;
        private final int left;
        private final int right;

        public Rect(final int top, final int bottom, final int left, final int right) {
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
        }
    }
}