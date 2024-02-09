package stroom.job.client.presenter;

import stroom.job.shared.ScheduleType;

import com.gwtplatform.mvp.client.UiHandlers;

public interface ScheduleUiHandlers extends UiHandlers {

    void onScheduleTypeChange(ScheduleType scheduleType);
}
