import * as React from "react";
import * as moment from "moment";

import { MetaRow } from "../../types";

interface Props {
  dataRow: MetaRow;
}

const MetaDetails: React.FunctionComponent<Props> = ({ dataRow }) => (
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
              <td>
                {moment(dataRow.meta.statusMs).format(
                  "MMMM Do YYYY, h:mm:ss a",
                )}
              </td>
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
              <td>
                {moment(dataRow.meta.createMs).format(
                  "MMMM Do YYYY, h:mm:ss a",
                )}
              </td>
            </tr>
            <tr>
              <td>Effective</td>
              <td>
                {moment(dataRow.meta.effectiveMs).format(
                  "MMMM Do YYYY, h:mm:ss a",
                )}
              </td>
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

export default MetaDetails;
