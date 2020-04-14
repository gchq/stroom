import * as React from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Tooltip from "components/Tooltip";

export const ProcessingSearchHelp = () => (
  <div className="processing__search__help">
    <Tooltip
      trigger={<FontAwesomeIcon icon="question-circle" size="lg" />}
      content={
        <div>
          <p>
            You may search for a tracker by part or all of a pipeline name.{" "}
          </p>
          <p>
            {" "}
            You may also use the following key words to filter the results:
          </p>
          <ul>
            <li>
              <code>is:enabled</code>
            </li>
            <li>
              <code>is:disabled</code>
            </li>
            <li>
              <code>is:complete</code>
            </li>
            <li>
              <code>is:incomplete</code>
            </li>
          </ul>
          <p>
            You may also sort the list to display the trackers that will next
            receive processing, using:
          </p>
          <ul>
            <li>
              <code>sort:next</code>
            </li>
          </ul>
        </div>
      }
    />
  </div>
);

export default ProcessingSearchHelp;
