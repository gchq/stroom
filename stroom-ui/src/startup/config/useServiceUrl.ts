import useConfig from "./useConfig";

const useServiceUrl = () => {
  const { authBaseServiceUrl, stroomBaseServiceUrl } = useConfig();

  return {
    tokenServiceUrl: `${authBaseServiceUrl}/token/v1`,
    userServiceUrl: `${authBaseServiceUrl}/user/v1`,
    authenticationServiceUrl: `${authBaseServiceUrl}/authentication/v1`,
    loginServiceUrl: `${authBaseServiceUrl}/login/v1`,
    authorisationServiceUrl: `${stroomBaseServiceUrl}/authorisation/v1`,
  };
};

export default useServiceUrl;
