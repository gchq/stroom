Prerequisites 

1. Git clone the github repo https://github.com/gchq/event-logging.git

1. cd into event-logging

1. git checkout 3.0

1. mvn clean install

To build the root project (without tests):

`mvn clean install -U -Dskip.surefire.tests=true`

To start Super Dev Mode use the following program arguments for Startup:

`-startupUrl stroom.jsp -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

(Open stroom at localhost:8888)

To use HTTPS with Jetty internal certificate

`-startupUrl stroom.jsp -server :ssl -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

To use HTTPS with a specific keystore

`-startupUrl stroom.jsp -server :keystore=/path/to/keystore,password=password,clientAuth=WANT -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

To use HTTPS with a specific keystore and password file

`-startupUrl stroom.jsp -server :keystore=/path/to/keystore,pwfile=/path/to/password/file,clientAuth=WANT -style PRETTY -logLevel INFO -war . -logdir . -gen . -extra . -workDir . stroom.app.AppSuperDevMode`

`clientAuth` can be `WANT` or `REQUIRED`