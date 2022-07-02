package stroom.core.sysinfo;

import stroom.util.sysinfo.HasSystemInfo.ParamInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SystemInfoService {

    List<SystemInfoResult> getAll();

    Set<String> getNames();

    List<ParamInfo> getParamInfo(final String providerName);

    Optional<SystemInfoResult> get(final String providerName);

    Optional<SystemInfoResult> get(final String providerName, final Map<String, String> params);
}
