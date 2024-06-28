package stroom.state.impl.dao;

import stroom.state.shared.StateType;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Provider;

import java.util.Objects;

public class DaoFactory {

    public static AbstractStateDao<?> create(final Provider<CqlSession> sessionProvider,
                                             final StateType stateType,
                                             final String tableName) {
        Objects.requireNonNull(stateType, "Null state type");
        return switch (stateType) {
            case STATE -> new StateDao(sessionProvider, tableName);
            case RANGED_STATE -> new RangedStateDao(sessionProvider, tableName);
            case TEMPORAL_STATE -> new TemporalStateDao(sessionProvider, tableName);
            case TEMPORAL_RANGED_STATE -> new TemporalRangedStateDao(sessionProvider, tableName);
            case SESSION -> new SessionDao(sessionProvider, tableName);
            default -> throw new RuntimeException("Unknown state type " + stateType);
        };
    }
}
