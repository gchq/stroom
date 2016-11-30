#!/bin/sh


perl -pi -e 's/"devModeRedirectEnabled" *value="false"/"devModeRedirectEnabled" value="true"/g;' stroom-app-client/src/main/resources/stroom/app/AppSuperDevMode.gwt.xml

