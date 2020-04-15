import * as React from "react";
import { MetaRow } from "components/MetaBrowser/types";

interface Props {
  dataRow: MetaRow;
}

const DataRetention: React.FunctionComponent<Props> = ({ dataRow }) => (
  <div className="tab-pane">
    <div className="RetentionDetails__container">
      <div className="RetentionDetails__table__container">
        <table className="RetentionDetails__table">
          <tbody>
            <tr>
              <td>Age</td>
              {/* <td>{details.nameValueMap.Age}</td> */}
            </tr>
            <tr>
              <td>Until</td>
              TODO
              {/* <td>{details.nameValueMap.Until}</td> */}
            </tr>
            <tr>
              <td>Rule</td>
              TODO
              {/* <td>{details.nameValueMap.Rule}</td> */}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
);

export default DataRetention;
