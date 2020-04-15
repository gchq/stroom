import * as React from "react";
import { SwitchedDocRefEditorProps } from "../DocRefEditor/types";
import DocRefEditor, { useDocRefEditor } from "../DocRefEditor";

const StroomStatsStoreEditor: React.FunctionComponent<
  SwitchedDocRefEditorProps
> = ({ docRefUuid }) => {
  const { editorProps } = useDocRefEditor({ docRefUuid });

  return (
    <DocRefEditor {...editorProps}>
      <h2>TODO - I.O.U a meaningful StroomStatsStoreEditor</h2>
    </DocRefEditor>
  );
};

export default StroomStatsStoreEditor;
