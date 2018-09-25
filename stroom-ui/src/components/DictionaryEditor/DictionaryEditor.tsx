import * as React from "react";
import {
  compose,
  lifecycle,
  renderComponent,
  branch,
  withHandlers,
  withProps
} from "recompose";
import { connect } from "react-redux";

import Loader from "../Loader";
import DocRefEditor from "../DocRefEditor";
import { Props as ButtonProps } from "../Button";
import { fetchDictionary, saveDictionary } from "./dictionaryResourceClient";
import { actionCreators, StoreStatePerId } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";

const { dictionaryUpdated } = actionCreators;

export interface Props {
  dictionaryUuid: string;
}

export interface ConnectState {
  dictionaryState: StoreStatePerId;
}
export interface ConnectDispatch {
  fetchDictionary: typeof fetchDictionary;
  dictionaryUpdated: typeof dictionaryUpdated;
  saveDictionary: typeof saveDictionary;
}

export interface WithHandlers {
  onDataChange: React.ChangeEventHandler<HTMLTextAreaElement>;
  onClickSave: React.MouseEventHandler;
}

export interface WithProps {
  actionBarItems: Array<ButtonProps>;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ dictionaryEditor }, { dictionaryUuid }) => ({
      dictionaryState: dictionaryEditor[dictionaryUuid]
    }),
    {
      fetchDictionary,
      dictionaryUpdated,
      saveDictionary
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { fetchDictionary, dictionaryUuid } = this.props;

      fetchDictionary(dictionaryUuid);
    }
  }),
  branch(
    ({ dictionaryState }) => !dictionaryState,
    renderComponent(() => <Loader message="Loading Dictionary" />)
  ),
  withHandlers<Props & ConnectState & ConnectDispatch, WithHandlers>({
    onDataChange: ({ dictionaryUuid, dictionaryUpdated }) => ({
      target: { value }
    }) => dictionaryUpdated(dictionaryUuid, { data: value }),
    onClickSave: ({ saveDictionary, dictionaryUuid }) => e =>
      saveDictionary(dictionaryUuid)
  }),
  withProps(({ dictionaryState: { isDirty, isSaving }, onClickSave }) => ({
    actionBarItems: [
      {
        icon: "save",
        disabled: !(isDirty || isSaving),
        title: isSaving ? "Saving..." : isDirty ? "Save" : "Saved",
        onClick: onClickSave
      }
    ]
  }))
);

const DictionaryEditor = ({
  dictionaryUuid,
  dictionaryState: { dictionary },
  onDataChange,
  actionBarItems
}: EnhancedProps) => (
  <DocRefEditor
    docRef={{
      type: "Dictionary",
      uuid: dictionaryUuid
    }}
    actionBarItems={actionBarItems}
  >
    <textarea value={dictionary && dictionary.data} onChange={onDataChange} />
  </DocRefEditor>
);

export default enhance(DictionaryEditor);
