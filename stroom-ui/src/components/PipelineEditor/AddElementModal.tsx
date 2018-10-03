import * as React from "react";
import { compose, withProps, withHandlers } from "recompose";
import { connect } from "react-redux";
import { Field, reduxForm, FormState } from "redux-form";

import IconHeader from "../IconHeader";
import Button from "../Button";
import ThemedModal from "../ThemedModal";
import { actionCreators } from "./redux";
import {
  StoreStateById as PipelineStatesStoreStateById,
  PendingNewElementType
} from "./redux/pipelineStatesReducer";
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
  newElementForm: FormState;
  initialValues?: {
    name: string;
  };
  pipelineState: PipelineStatesStoreStateById;
  pendingNewElement?: PendingNewElementType;
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
    ({ form, pipelineEditor: { pipelineStates } }, { pipelineId }) => {
      const pipelineState: PipelineStatesStoreStateById =
        pipelineStates[pipelineId];
      let initialValues;

      const { pendingNewElement } = pipelineState;
      if (pendingNewElement) {
        initialValues = {
          name: pendingNewElement.elementDefinition.type
        };
      }

      return {
        // state
        newElementForm: form.newElementName,
        pendingNewElement,
        pipelineState,
        initialValues
      };
    },
    { pipelineElementAddConfirmed, pipelineElementAddCancelled }
  ),
  reduxForm({
    form: "newElementName",
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true
  }),
  // Properties from owner
  withHandlers({
    // from withNewElementDefinition in owner // Redux form
    onConfirmNewElement: ({
      pipelineElementAddConfirmed,
      pipelineId,
      newElementForm: {
        values: { name }
      },
      reset
    }) => () => {
      pipelineElementAddConfirmed(pipelineId, name);
      reset();
    },
    onCancelNewElement: ({
      pipelineElementAddCancelled,
      reset,
      pipelineId
    }) => () => {
      pipelineElementAddCancelled(pipelineId);
      reset();
    },
    onUniqueNameCheck: ({ pipelineState: { pipeline } }) => (value: string) => {
      const elementNames = getAllElementNames(pipeline);
      return !elementNames.includes(value);
    }
  }),
  withProps(({ invalid, submitting, pendingNewElement }) => ({
    submitDisabled: invalid || submitting,
    isOpen: !!pendingNewElement
  }))
);

const AddElementModal = ({
  pipelineState: { pipeline },
  isOpen,
  submitDisabled,
  onConfirmNewElement,
  onCancelNewElement,
  onUniqueNameCheck
}: EnhancedProps) => (
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
            component="input"
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
          onClick={onConfirmNewElement}
        />
        <Button text="Cancel" onClick={onCancelNewElement} />
      </React.Fragment>
    }
  />
);

// AddElementModal.propTypes = {
//   pipelineId: PropTypes.string.isRequired,
// };

export default enhance(AddElementModal);
