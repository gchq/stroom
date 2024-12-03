package stroom.dashboard.shared;

import java.util.List;

public interface HasSelectionFilterBuilder<T extends ComponentSettings, B extends ComponentSettings.AbstractBuilder<T, ?>> {

    B selectionFilter(List<ComponentSelectionHandler> selectionFilter);
}
