package stroom.dashboard.shared;

import java.util.List;

public interface HasSelectionQueryBuilder<T extends ComponentSettings, B extends ComponentSettings.AbstractBuilder<T, ?>> {

    B selectionQuery(List<ComponentSelectionHandler> selectionQuery);
}
