package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.PropertyPath;

import java.util.List;
import java.util.Optional;

public interface ConfigPropertyDao extends HasIntCrud<ConfigProperty> {

    boolean delete(String name);

    boolean delete(PropertyPath propertyPath);

    List<ConfigProperty> list();

    Optional<ConfigProperty> fetch(final String propertyName);
}
