<%@page import="stroom.util.BuildInfoUtil"%>
<html>
<head>
<link rel="stylesheet" href="standard.css" />
</head>
<body>
<h1>Stroom Proxy <%= BuildInfoUtil.getBuildVersion() %> built on <%= BuildInfoUtil.getBuildDate() %></h1>


<p>Send data to <%=request.getRequestURL()%>datafeed</p>

</body>
</html>
