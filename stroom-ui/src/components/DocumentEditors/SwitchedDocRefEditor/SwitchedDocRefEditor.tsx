import * as React from "react";

import PathNotFound from "../../PathNotFound";
import useRecentItems from "lib/useRecentItems";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { SwitchedDocRefEditorProps } from "../DocRefEditor/types";
import { docRefEditorClasses } from "./types";

const SwitchedDocRefEditor: React.FunctionComponent<SwitchedDocRefEditorProps> = ({
  docRefUuid,
}) => {
  const { addRecentItem } = useRecentItems();
  const { findDocRefWithLineage } = useDocumentTree();
  const { node: docRef } = React.useMemo(
    () => findDocRefWithLineage(docRefUuid),
    [findDocRefWithLineage, docRefUuid],
  );

  React.useEffect(() => {
    addRecentItem(docRef);
  }, [docRef, addRecentItem]);

  const EditorClass = docRefEditorClasses[docRef.type];
  if (!!EditorClass) {
    return <EditorClass docRefUuid={docRef.uuid} />;
  } else {
    return (
      <PathNotFound
        message={`no editor provided for this doc ref type ${docRef.type}`}
      />
    );
  }
};

export default SwitchedDocRefEditor;
