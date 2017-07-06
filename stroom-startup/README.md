To start Super Dev Mode use the following program arguments for Startup:

`-startupUrl stroom.jsp -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

To use HTTPS with Jetty internal certificate

`-startupUrl stroom.jsp -server :ssl -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

To use HTTPS with a specific keystore

`-startupUrl stroom.jsp -server :keystore=/path/to/keystore,password=password,clientAuth=WANT -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

To use HTTPS with a specific keystore and password file

`-startupUrl stroom.jsp -server :keystore=/path/to/keystore,pwfile=/path/to/password/file,clientAuth=WANT -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

`clientAuth` can be `WANT` or `REQUIRED`