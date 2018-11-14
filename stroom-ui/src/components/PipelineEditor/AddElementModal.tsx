import * as React from "react";
import { compose, withProps, withHandlers } from "recompose";
import { connect } from "react-redux";
import { Formik, Field } from "formik";

import IconHeader from "../IconHeader";
import Button from "../Button";
import ThemedModal from "../ThemedModal";
import { actionCreators } from "./redux";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";
import { getAllElementNames } from "./pipelineUtils";
import { required, minLength2 } from "../../lib/reduxFormUtils";
import { GlobalStoreState } from "../../startup/reducers";

const {
  pipelineElementAddConfirmed,
  pipelineElementAddCancelled
} = actionCreators;

export interface Props {
  pipelineId: string;
}

interface ConnectState {
  pipelineState: PipelineStatesStoreStateById;
}

interface ConnectDispatch {
  pipelineElementAddConfirmed: typeof pipelineElementAddConfirmed;
  pipelineElementAddCancelled: typeof pipelineElementAddCancelled;
}

interface WithHandlers {
  onConfirmNewElement: () => void;
  onCancelNewElement: () => void;
  onUniqueNameCheck: (value: string) => boolean;
}

interface WithProps {
  submitDisabled: boolean;
  isOpen: boolean;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { pipelineStates } }, { pipelineId }) => {
      const pipelineState: PipelineStatesStoreStateById =
        pipelineStates[pipelineId];

      return {
        // state
        pipelineState
      };
    },
    { pipelineElementAddConfirmed, pipelineElementAddCancelled }
  ),
  // Properties from owner
  withHandlers({
    // from withNewElementDefinition in owner // Redux form
    onCancelNewElement: ({ pipelineElementAddCancelled, pipelineId }) => () => {
      pipelineElementAddCancelled(pipelineId);
    },
    onUniqueNameCheck: ({ pipelineState: { pipeline } }) => (value: string) => {
      const elementNames = getAllElementNames(pipeline);
      return !elementNames.includes(value);
    }
  }),
  withProps(
    ({ invalid, submitting, pipelineState: { pendingNewElement } }) => ({
      submitDisabled: invalid || submitting,
      isOpen: !!pendingNewElement
    })
  )
);

interface FormValues {
  name: string;
}

const AddElementModal = ({
  isOpen,
  submitDisabled,
  pipelineState: { pendingNewElement },
  pipelineId,
  pipelineElementAddConfirmed,
  onCancelNewElement,
  onUniqueNameCheck
}: EnhancedProps) => (
  <Formik<FormValues>
    initialValues={{
      name: pendingNewElement ? pendingNewElement.elementDefinition.type : ""
    }}
    onSubmit={values => {
      pipelineElementAddConfirmed(pipelineId, values.name);
    }}
  >
    {({ submitForm }: Formik) => (
      <ThemedModal
        isOpen={isOpen}
        onRequestClose={onCancelNewElement}
        header={<IconHeader icon="file" text="Add New Element" />}
        content={
          <form>
            <div>
              <label>Name</label>
              <Field
                name="name"
                type="text"
                placeholder="Name"
                validate={[required, minLength2, onUniqueNameCheck]}
                autoFocus
              />
            </div>
          </form>
        }
        actions={
          <React.Fragment>
            <Button
              text="Submit"
              disabled={submitDisabled}
              onClick={submitForm}
            />
            <Button text="Cancel" onClick={onCancelNewElement} />
          </React.Fragment>
        }
      />
    )}
  </Formik>
);

export default enhance(AddElementModal);
