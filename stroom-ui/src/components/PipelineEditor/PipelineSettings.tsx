import * as React from "react";
import { compose, withProps } from "recompose";
import { connect } from "react-redux";
import { Formik, Field } from "formik";

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
  settings?: PipelineSettingsStoreStatePerId;
}

interface ConnectDispatch {
  pipelineSettingsClosed: typeof pipelineSettingsClosed;
  pipelineSettingsUpdated: typeof pipelineSettingsUpdated;
}

interface WithProps {
  onCancel: () => any;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { settings, pipelineStates } }, { pipelineId }) => ({
      settings: settings[pipelineId],
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
  withProps(({ pipelineId, pipelineSettingsClosed }) => ({
    onCancel: () => {
      pipelineSettingsClosed(pipelineId);
    }
  }))
);

interface FormValues {
  description: string;
}

const PipelineSettings = ({
  onCancel,
  settings,
  pipelineSettingsUpdated,
  pipelineId
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{ description: "" }}
    onSubmit={values => {
      pipelineSettingsUpdated(pipelineId, values.description);
    }}
  >
    {({ submitForm }) => (
      <ThemedModal
        isOpen={!!settings && settings.isOpen}
        onRequestClose={onCancel}
        header={<IconHeader icon="cog" text="Pipeline Settings" />}
        content={
          <form>
            <div>
              <label>Description</label>
              <Field
                name="description"
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
              // disabled={invalid || submitting}
              onClick={submitForm}
            />
            <Button text="Cancel" onClick={onCancel} />
          </React.Fragment>
        }
      />
    )}
  </Formik>
);

export default enhance(PipelineSettings);
