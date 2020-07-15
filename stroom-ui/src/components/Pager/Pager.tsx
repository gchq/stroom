import * as React from "react";
import Button from "components/Button/Button";
import { useEffect, useState } from "react";
import { PageRequest } from "../Account/api/types";

interface Page {
  from: number;
  to: number;
  of?: number;
}

export interface PagerProps {
  page: Page;
  onChange?: (request: PageRequest) => void;
}

export const Pager: React.FunctionComponent<PagerProps> = ({
  page,
  onChange,
}) => {
  // Store the state for the page we would like to request.
  const [pageRequest, setPageRequest] = useState<PageRequest>({
    offset: 0,
    length: 100,
  });

  // Update the page request with the resultant from position if it is different.
  useEffect(() => {
    if (pageRequest.offset !== page.from) {
      setPageRequest({
        ...pageRequest,
        offset: page.from,
      });
    }
  }, [pageRequest, setPageRequest, page.from]);

  const onFirst = () => {
    const newRequest = {
      ...pageRequest,
      offset: 0,
    };
    setPageRequest(newRequest);
    onChange(newRequest);
  };

  const onBack = () => {
    const newRequest = {
      ...pageRequest,
      offset: Math.max(0, pageRequest.offset - pageRequest.length),
    };
    setPageRequest(newRequest);
    onChange(newRequest);
  };

  const onForward = () => {
    const newRequest = {
      ...pageRequest,
      offset: pageRequest.offset + pageRequest.length,
    };
    setPageRequest(newRequest);
    onChange(newRequest);
  };

  const onLast = () => {
    const newRequest = {
      ...pageRequest,
      offset: Number.MAX_SAFE_INTEGER,
    };
    setPageRequest(newRequest);
    onChange(newRequest);
  };

  const onRefresh = () => {
    onChange(pageRequest);
  };

  return (
    <div className="Pager">
      <div className="Pager__label">
        {page.from !== undefined ? page.from + 1 : "?"}
      </div>
      <div className="Pager__label">to</div>
      <div className="Pager__label">
        {page.to !== undefined ? page.to : "?"}
      </div>
      <div className="Pager__label">of</div>
      <div className="Pager__label">
        {page.of !== undefined ? page.of : "?"}
      </div>

      <Button
        size="small"
        appearance="icon"
        action="primary"
        title="First"
        disabled={page.from === 0}
        allowFocus={false}
        onClick={onFirst}
      >
        <img
          className="Pager__button"
          alt="First"
          title="First"
          src={require("images/pager/fast-backward.svg")}
        />
      </Button>
      <Button
        size="small"
        appearance="icon"
        action="primary"
        title="Back"
        disabled={page.from === 0}
        allowFocus={false}
        onClick={onBack}
      >
        <img
          className="Pager__button"
          alt="Back"
          title="Back"
          src={require("images/pager/step-backward.svg")}
        />
      </Button>
      <Button
        size="small"
        appearance="icon"
        action="primary"
        title="Forward"
        disabled={
          page.to !== undefined && page.of !== undefined && page.to >= page.of
        }
        allowFocus={false}
        onClick={onForward}
      >
        <img
          className="Pager__button"
          alt="Forward"
          title="Forward"
          src={require("images/pager/step-forward.svg")}
        />
      </Button>
      <Button
        size="small"
        appearance="icon"
        action="primary"
        title="Last"
        disabled={
          page.of === undefined || (page.to !== undefined && page.to >= page.of)
        }
        allowFocus={false}
        onClick={onLast}
      >
        <img
          className="Pager__button"
          alt="Last"
          title="Last"
          src={require("images/pager/fast-forward.svg")}
        />
      </Button>
      <Button
        size="small"
        appearance="icon"
        action="primary"
        title="Refresh"
        allowFocus={false}
        onClick={onRefresh}
      >
        <img
          className="Pager__button"
          alt="Refresh"
          title="Refresh"
          src={require("images/pager/refresh.svg")}
        />
      </Button>
    </div>
  );
};
