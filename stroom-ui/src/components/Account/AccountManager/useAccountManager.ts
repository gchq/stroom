import { useAccountResource } from "../api";
import { Account } from "../types";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ResultPage, SearchAccountRequest } from "../api/types";
import useDateUtil from "../../../lib/useDateUtil";

interface UserSearchApi {
  columns: any[];
  resultPage: ResultPage<Account>;
  // selectedUser: string;
  remove: (userId: number) => void;
  // changeSelectedUser: (userId: string) => void;
  request: SearchAccountRequest;
  setRequest: (request: SearchAccountRequest) => void;
  // search: (request: SearchAccountRequest) => void;
}

const defaultResultPage: ResultPage<Account> = {
  values: [],
  pageResponse: {
    offset: 0,
    length: 0,
    total: undefined,
    exact: false,
  },
};

const defaultRequest: SearchAccountRequest = {
  pageRequest: {
    offset: 0,
    length: 100,
  },
};

const useAccountManager = (): UserSearchApi => {
  // const {
  //   users,
  //   selectedUser,
  //   setSelectedUser,
  //   setUsers,
  // } = useAccountManagerState();
  const [resultPage, setResultPage] = useState<ResultPage<Account>>(
    defaultResultPage,
  );
  const [request, setRequest] = useState(defaultRequest);
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

  // const search = useCallback(
  //   (request: SearchAccountRequest) => {
  //     searchApi(request).then((resultPage) => setUsers(resultPage));
  //   },
  //   [searchApi, setUsers],
  // );

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
        Header: "Email",
        accessor: "email",
        maxWidth: 190,
        // filterMethod: (filter: any, row: any) => filterRow(row, filter),
      },
      {
        Header: "Account Status",
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
    // selectedUser,
    remove,
    // changeSelectedUser: setSelectedUser,
    request,
    setRequest,
    // search,
  };
};

export default useAccountManager;
