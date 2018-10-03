import * as React from "react";
import { compose, withProps } from "recompose";
import { connect } from "react-redux";
import { Field, reduxForm, FormState, InjectedFormProps } from "redux-form";

import Button from "../Button";
import IconHeader from "../IconHeader";
import ThemedModal from "../ThemedModal";
import { actionCreators } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import { StoreStatePerId as PipelineSettingsStoreStatePerId } from "./redux/pipelineSettingsReducer";

const { pipelineSettingsClosed, pipelineSettingsUpdated } = actionCreators;

export interface Props {
  pipelineId: string;
}

interface ConnectState {
  pipelineSettingsForm: FormState;
  settings: PipelineSettingsStoreStatePerId;
}

interface ConnectDispatch {
  pipelineSettingsClosed: typeof pipelineSettingsClosed;
  pipelineSettingsUpdated: typeof pipelineSettingsUpdated;
}

interface WithProps {
  onConfirm: () => any;
  onCancel: () => any;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    InjectedFormProps,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (
      {
        pipelineEditor: { settings, pipelineStates },
        form: { pipelineSettings }
      },
      { pipelineId }
    ) => ({
      settings: settings[pipelineId],
      pipelineSettingsForm: pipelineSettings,
      initialValues: {
        description:
          pipelineStates[pipelineId] && pipelineStates[pipelineId].pipeline
            ? pipelineStates[pipelineId]!.pipeline!.description
            : ""
      }
    }),
    {
      pipelineSettingsClosed,
      pipelineSettingsUpdated
    }
  ),
  reduxForm({
    form: "pipelineSettings",
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true
  }),
  withProps(
    ({
      pipelineId,
      pipelineSettingsForm,
      pipelineSettingsUpdated,
      pipelineSettingsClosed,
      reset
    }) => ({
      onConfirm: () => {
        pipelineSettingsUpdated(
          pipelineId,
          pipelineSettingsForm.values.description
        );
        reset();
      },
      onCancel: () => {
        reset();
        pipelineSettingsClosed(pipelineId);
      }
    })
  )
);

const PipelineSettings = ({
  pipelineId,
  onConfirm,
  onCancel,
  settings: { isOpen },
  invalid,
  submitting
}: EnhancedProps) => (
  <ThemedModal
    isOpen={isOpen}
    onRequestClose={onCancel}
    header={<IconHeader icon="cog" text="Pipeline Settings" />}
    content={
      <form>
        <div>
          <label>Description</label>
          <Field
            name="description"
            component="textarea"
            type="text"
            placeholder="Description"
            autoFocus
          />
        </div>
      </form>
    }
    actions={
      <React.Fragment>
        <Button
          text="Submit"
          disabled={invalid || submitting}
          onClick={onConfirm}
        />
        <Button text="Cancel" onClick={onCancel} />
      </React.Fragment>
    }
  />
);

export default enhance(PipelineSettings);
