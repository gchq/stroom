import { useAccountResource } from "../api";
import { Account } from "../types";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ResultPage, SearchAccountRequest } from "../api/types";
import useDateUtil from "../../../lib/useDateUtil";

interface UseAccountManager {
  columns: any[];
  resultPage: ResultPage<Account>;
  remove: (userId: number) => void;
  request: SearchAccountRequest;
  setRequest: (request: SearchAccountRequest) => void;
}

const initialResultPage: ResultPage<Account> = {
  values: [],
  pageResponse: {
    offset: 0,
    length: 0,
    total: undefined,
    exact: false,
  },
};

const initialRequest: SearchAccountRequest = {
  pageRequest: {
    offset: 0,
    length: 100,
  },
  sortList: [
    {
      id: "userId",
      desc: false,
    },
  ],
};

const useAccountManager = (): UseAccountManager => {
  const [resultPage, setResultPage] = useState<ResultPage<Account>>(
    initialResultPage,
  );
  const [request, setRequest] = useState(initialRequest);
  const {
    search: searchApi,
    remove: removeUserUsingApi,
  } = useAccountResource();

  useEffect(() => {
    searchApi(request).then((response) => {
      if (response) {
        setResultPage(response);
      }
    });
  }, [searchApi, setResultPage, request]);

  const remove = useCallback(
    (userId: number) => {
      removeUserUsingApi(userId).then(() =>
        searchApi(request).then((resultPage) => setResultPage(resultPage)),
      );
    },
    [removeUserUsingApi, searchApi, request, setResultPage],
  );

  const { toDateString } = useDateUtil();

  const columns = useMemo(
    () => [
      {
        Header: "",
        accessor: "id",
        // Cell: IdCell,
        // filterable: false,
        show: false,
      },
      {
        id: "userId",
        Header: "User Id",
        accessor: "userId",
        maxWidth: 150,
        // filterMethod: (filter: any, row: any) => filterRow(row, filter),
      },
      {
        Header: "Email",
        accessor: "email",
        maxWidth: 200,
        // filterMethod: (filter: any, row: any) => filterRow(row, filter),
      },
      {
        Header: "Status",
        accessor: ({ locked, inactive, enabled }) =>
          locked
            ? "Locked"
            : inactive
            ? "Inactive"
            : enabled
            ? "Enabled"
            : "Disabled",
        maxWidth: 100,
        // Cell: StateCell,
        // Filter: getStateCellFilter,
      },
      {
        Header: "Last Sign In",
        accessor: ({ lastLoginMs }) => lastLoginMs && toDateString(lastLoginMs),
        // Cell: (row: RowInfo) => moment(row.row.value).format(dateFormat),
        maxWidth: 205,
        // filterable: false,
      },
      {
        Header: "Sign In Failures",
        accessor: "loginFailures",
        maxWidth: 100,
      },
      {
        Header: "Comments",
        accessor: "comments",
        // filterMethod: (filter: any, row: any) => filterRow(row, filter),
      },
    ],
    [toDateString],
  );

  return {
    columns,
    resultPage,
    remove,
    request,
    setRequest,
  };
};

export default useAccountManager;
