# Wave 18: Handlers wired in Java

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 28 October 2026, after wave 17, from the code as it
then stood.

Every component wires its widgets' handlers in Java, in its constructor, so that each handler is in
one place and in one form, whether the widget is declared in the template or made in Java (agreed 27
October 2026; ISSUES.md, "Some components wire their handlers with `@UiHandler`"). Before this wave
191 `@UiHandler` methods in 71 classes (69 in the app, 2 in the widget library) still bound handlers
from their templates. Nothing a user sees changes.

## The rule

Each `@UiHandler("field")` method stays a method, now private, and the constructor wires it straight
after the template is bound (`createAndBindUi(this)`, where GWT's generated code bound it before, so
the handlers run in the same order):

- **A Stroom button's click** (`Button`, `ToolbarButton`, `LinkButton`, `FabButton`), when the method
  doesn't use the event: `field.setOnClick(this::method)`. The callback runs from the button's own
  click handler, so it fires exactly when the `ClickEvent` did.
- **A `TickBox`'s, `ValueSpinner`'s or `RadioGroup`'s value change**, when the method uses the event
  only for its value: `field.setOnChange(this::method)` (the method takes the value) or
  `field.setOnChange(value -> method())`. Each widget calls its callback beside the
  `ValueChangeEvent` it fires.
- **Anything else** (a `SelectionBox`'s value change, a text box's keys, focus and blur, a GWT
  `Label`'s click, a method that reads the event): `field.addXxxHandler(this::method)`.

A callback holds one function, so it is used only where nothing else sets it: not where the class
sets the same callback elsewhere, or hands the widget out through a getter (the pager's refresh
button, whose callers set its `setOnClick`, keeps `addClickHandler`). Widgets only in the template
(`DateTimePicker`'s four Reset links) get a `@UiField`.

Checkstyle refuses `com.google.gwt.uibinder.client.UiHandler` (`UiHandlerAnnotation`), as it refuses
GWT's widgets.

## Done (28 October 2026)

All 191, by a script (`w18convert.py` in the session's scratchpad), in one commit: 44 to
`setOnClick`, 37 to `setOnChange`, 113 to `addXxxHandler` (some methods handle two widgets). The
methods' `@param event` javadoc went where they no longer take the event. Checked by compiling both
apps (Java and GWT), checkstyle with the new rule, the JVM tests and a full workbench run with the
leak check.
