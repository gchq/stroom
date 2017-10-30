<%@page import="stroom.util.BuildInfoUtil"%>INFO,HTTP,OK
INFO,STROOM_PROXY,Build version <%= BuildInfoUtil.getBuildVersion() %>
INFO,STROOM_PROXY,Build date <%= BuildInfoUtil.getBuildDate() %>
INFO,STROOM_PROXY,Up date <%= BuildInfoUtil.getUpDate() %>
