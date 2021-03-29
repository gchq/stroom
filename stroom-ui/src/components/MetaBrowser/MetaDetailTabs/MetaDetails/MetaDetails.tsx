import * as React from "react";

import { MetaRow } from "../../types";
import useDateUtil from "../../../../lib/useDateUtil";

interface Props {
  dataRow: MetaRow;
}

export const MetaDetails: React.FunctionComponent<Props> = ({ dataRow }) => {
  const { toDateString } = useDateUtil();

  return (
    <div className="tab-pane">
      <div className="StreamDetails__container">
        <div className="StreamDetails__table__container">
          <table className="StreamDetails__table">
            <tbody>
              <tr>
                <td>Stream ID</td>
                <td>
                  <code>{dataRow.meta.id}</code>
                </td>
              </tr>
              <tr>
                <td>Status</td>
                <td>
                  <code> {dataRow.meta.status}</code>
                </td>
              </tr>
              <tr>
                <td>Status MS</td>
                <td>{toDateString(dataRow.meta.statusMs)}</td>
              </tr>
              <tr>
                <td>Stream Task ID</td>
                <td>
                  <code>{`${dataRow.meta.processTaskId}`}</code>
                </td>
              </tr>
              <tr>
                <td>Parent Stream ID</td>
                <td>
                  <code>{`${dataRow.meta.parentDataId}`}</code>
                </td>
              </tr>
              <tr>
                <td>Created</td>
                <td> {toDateString(dataRow.meta.createMs)} </td>
              </tr>
              <tr>
                <td>Effective</td>
                <td>{toDateString(dataRow.meta.effectiveMs)}</td>
              </tr>
              <tr>
                <td>Stream processor uuid</td>
                TODO
                {/* <td>{details.stream.processor.id}</td> */}
              </tr>
              <tr>
                <td>Files</td>
                TODO
                {/* <td>{details.fileNameList}</td> */}
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
