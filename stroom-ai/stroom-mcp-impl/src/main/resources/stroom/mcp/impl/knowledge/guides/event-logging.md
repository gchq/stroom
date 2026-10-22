# Event-logging schema: working guide

Namespace `event-logging:3`, in every version: 4.x schemas keep it, so it doesn't mean a 3.x schema. The
authority is the XSD installed in Stroom: use `check_events`, which picks the version from the document's
`xsi:schemaLocation` (e.g. `file://event-logging-v4.1.0.xsd`). A new translation writes the version the user chooses
from those this Stroom holds (`draft_translation_mapping` asks, the newest recommended, whatever its major version);
an existing one keeps its own.

## Shape of one event

```
<Events xmlns="event-logging:3" xsi:schemaLocation="event-logging:3 file://event-logging-vX.Y.Z.xsd" Version="X.Y.Z">
  <Event>
    <EventTime><TimeCreated>2026-09-28T10:00:00.000Z</TimeCreated></EventTime>
    <EventSource>
      <System><Name>...</Name><Environment>...</Environment></System>
      <Generator>...</Generator>
      <Device><HostName>...</HostName><IPAddress>...</IPAddress></Device>
      <!-- optional: Client, Server, User, RunAs, Interactive -->
    </EventSource>
    <EventDetail>
      <TypeId>...</TypeId>               <!-- stable id for this kind of event -->
      <Description>...</Description>
      <!-- exactly one action element, e.g. Authenticate, Authorise, View, Create, Update, Delete,
           Process, Search, Send, Receive, Network, Alert, Import, Export, Install, Uninstall -->
    </EventDetail>
  </Event>
</Events>
```

Element order matters: the XSD is a sequence. A misplaced element fails validation with
"Invalid content was found starting with element X. One of Y is expected", which names the element
that should come next.

## Rules the quality check applies (`check_events`)

- `TimeCreated` is UTC in `yyyy-MM-ddTHH:mm:ss.SSSZ`. `stroom:format-date(value, pattern)` produces it.
- `System/Name`, `System/Environment`, `Generator` and `TypeId` are set.
- `EventSource/Device` identifies the host the event came from.
- `EventDetail` holds exactly one action element.
- No empty elements: leave an element out rather than writing it empty (an empty `TimeCreated` fails
  the schema; an empty `HostName` passes it but carries no information).

## Authenticate, the common case

```
<Authenticate>
  <Action>Logon</Action>                 <!-- Logon, Logoff, ChangePassword, ... -->
  <User><Id>alice</Id></User>
  <Outcome><Success>false</Success><Description>Bad password</Description></Outcome>
</Authenticate>
```

`Outcome` is omitted on success in many environments; include `Success=false` with a
`Description` for failures.

## Network, Update and Alert: the other common cases

A firewall's or proxy's connection allowed or denied is `Network`, with the action as its child element
(`Permit`, `Deny`, `Open`, `Close`, `Connect`, ...), not values in `Unknown/Data`:

```
<Network>
  <Deny>                                 <!-- Permit for allowed, Deny for blocked -->
    <Source><Device><IPAddress>203.0.113.45</IPAddress></Device><Port>49822</Port>
            <TransportProtocol>TCP</TransportProtocol></Source>   <!-- TCP, UDP, ICMP, IGMP, Other -->
    <Destination><Device><IPAddress>192.0.2.25</IPAddress></Device><Port>22</Port></Destination>
    <Data Name="rule_id" Value="2003"/>
  </Deny>
</Network>
```

In a mapping: `EventDetail/Network/Deny/Source/Device/IPAddress`, `.../Source/Port`,
`.../Destination/Device/IPAddress`, `.../Destination/Port`, `.../Data` (with `data_name`).

A configuration change is `Update`, the new state under `After` (`Before` for the old):
`EventDetail/Update/After/Configuration/Type` and `.../Configuration/Description`.

A command or program run (sudo, a shell's history, a scheduled job) is `Process`:
`EventDetail/Process/Action` (`Execute`), `.../Process/Type` (`OS`, `Service`, `Application`), `.../Process/Command`
and `.../Process/Arguments`. The account it ran as (sudo's `USER=`) is `EventSource/RunAs/Id`, beside the user who
ran it in `EventSource/User/Id`. `Authorise` is for a permission granted or refused on an object (a role, a file's
access), not for a command a user was allowed to run.

A health or status message is `Alert`: `EventDetail/Alert/Type` (Vulnerability, IDS, Malware, Network, Change,
Error, Other) and `EventDetail/Alert/Severity` (Info, Minor, Major, Critical).

`Unknown` says what happened isn't known. It is for records no action element describes, not a way round a
schema error in one: `draft_translation_mapping` drafts these elements where the sample's values show them,
and `build_translation_xslt` refuses `allow_unknown` for connections and logons, giving the rules to use.
