import useConfig from "./useConfig";

const useServiceUrl = () => {
  const { stroomBaseServiceUrl } = useConfig();

  return {
    tokenServiceUrl: `${stroomBaseServiceUrl}/token/v1`,
    userServiceUrl: `${stroomBaseServiceUrl}/user/v1`,
    authenticationServiceUrl: `${stroomBaseServiceUrl}/authentication/v1`,
    loginServiceUrl: `${stroomBaseServiceUrl}/login/v1`,
    authorisationServiceUrl: `${stroomBaseServiceUrl}/authorisation/v1`,
  };
};

export default useServiceUrl;
