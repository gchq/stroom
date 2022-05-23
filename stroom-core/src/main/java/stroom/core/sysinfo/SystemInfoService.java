package stroom.core.sysinfo;

import stroom.util.sysinfo.SystemInfoResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SystemInfoService {

    List<SystemInfoResult> getAll();

    Set<String> getNames();

    Map<String, String> getParamInfo(final String name);

    Optional<SystemInfoResult> get(final String name);

    Optional<SystemInfoResult> get(final String name, final Map<String, String> params);
}
