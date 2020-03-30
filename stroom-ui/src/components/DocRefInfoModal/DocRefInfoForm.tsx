import * as React from "react";

import Loader from "components/Loader";
import { useDocRefInfo } from "components/DocumentEditors/api/explorer";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

interface Props {
  docRef?: DocRefType;
}

const DocRefInfoForm: React.FunctionComponent<Props> = ({ docRef }) => {
  const docRefInfo = useDocRefInfo(docRef);

  if (!docRefInfo) {
    return <Loader message="Awaiting DocRef info..." />;
  }

  const { createTime, updateTime } = docRefInfo;

  const formattedCreateTime = new Date(createTime).toLocaleString("en-GB", {
    timeZone: "UTC",
  });
  const formattedUpdateTime = new Date(updateTime).toLocaleString("en-GB", {
    timeZone: "UTC",
  });

  return (
    <form className="DocRefInfo">
      <div className="DocRefInfo__type">
        <label>Type</label>
        <input type="text" value={docRefInfo.docRef.type} />
      </div>
      <div className="DocRefInfo__uuid">
        <label>UUID</label>
        <input type="text" value={docRefInfo.docRef.uuid} />
      </div>
      <div className="DocRefInfo__name">
        <label>Name</label>
        <input type="text" value={docRefInfo.docRef.name} />
      </div>

      <div className="DocRefInfo__createdBy">
        <label>Created by</label>
        <input type="text" value={docRefInfo.createUser} />
      </div>
      <div className="DocRefInfo__createdOn">
        <label>at</label>
        <input type="text" value={formattedCreateTime} />
      </div>
      <div className="DocRefInfo__updatedBy">
        <label>Updated by</label>
        <input type="text" value={docRefInfo.updateUser} />
      </div>
      <div className="DocRefInfo__updatedOn">
        <label>at</label>
        <input type="text" value={formattedUpdateTime} />
      </div>
      <div className="DocRefInfo__otherInfo">
        <label>Other Info</label>
        <input type="text" value={docRefInfo.otherInfo} />
      </div>
    </form>
  );
};

export default DocRefInfoForm;
