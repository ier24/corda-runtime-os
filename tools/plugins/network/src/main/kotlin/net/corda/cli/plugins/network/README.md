# Network Plugin
This is a plug-in for [Corda CLI plugin host](https://github.com/corda/corda-cli-plugin-host) for membership operations.

> The commands below assume you have access to corda-cli.sh   
> To use this plugin, build the JAR with `./gradlew :tools:plugins:network:build`, move the JAR to 
> `corda-cli-plugin-host/build/plugins/` and run the commands as shown below by locating corda-cli.sh in 
> `corda-cli-plugin-host/build/generatedScripts/`.

# Generate Group Policy

This is a sub-command under the `network` plugin for generating a GroupPolicy.json file. This is a new command.

Running `groupPolicy` without any command line arguments prints a sample GroupPolicy file for the user to manually tweak.
```shell
./corda-cli.sh network groupPolicy
```

NOTE: The `groupPolicy` sub-command is also available under the `mgm` command in order to preserve backwards compatibility.

Alternatively, the following command line arguments can be used to define the static network section of the GroupPolicy:

| Argument            | Description                                                          |
|---------------------|----------------------------------------------------------------------|
| --file, -f          | Path to a JSON or YAML file that contains static network information |
| --name              | Member's X.500 name                                                  |
| --endpoint          | Endpoint base URL                                                    |
| --endpoint-protocol | Version of end-to-end authentication protocol                        |

To generate GroupPolicy using file input:
> Sample files are available [here](#sample-files).
```shell
./corda-cli.sh network groupPolicy --file="app/build/resources/src.yaml"
```
Note:
1. Only one of `memberNames` and `members` blocks may be present.
2. Single endpoint is assumed for all members when `memberNames` is used.
3. Endpoint information specified under `members` overrides endpoint information set at the root level. An error is thrown if endpoint information is not provided at all.
4. The `groupPolicy` sub-command is also available under the `mgm` command in order to preserve backwards compatibility.

To generate GroupPolicy using string parameters:
```shell
./corda-cli.sh network groupPolicy --name="C=GB, L=London, O=Member1" --name="C=GB, L=London, O=Member2" --endpoint-protocol=5 --endpoint="http://dummy-url"
```
Note:
1. Passing one or more `--name` without specifying endpoint information will throw an error.
2. Not passing any `--name` will return a GroupPolicy with an empty list of static members.
3. Single endpoint is assumed for all members.
4. The `groupPolicy` sub-command is also available under the `mgm` command in order to preserve backwards compatibility.

## Sample files

1. Sample JSON with `memberNames`
```json
{
  "endpoint": "http://dummy-url",
  "endpointProtocol": 5,
  "memberNames": ["C=GB, L=London, O=Member1", "C=GB, L=London, O=Member2"]
}
```

2. Sample JSON with `members`
```json
{
  "members": [
    {
      "name": "C=GB, L=London, O=Member1",
      "status": "PENDING",
      "endpoint": "http://dummy-url",
      "endpointProtocol": 5
    },
    {
      "name": "C=GB, L=London, O=Member2",
      "endpoint": "http://dummy-url2",
      "endpointProtocol": 5
    }
  ]
}
```

3. Sample YAML with `memberNames`
```yaml
endpoint: "http://dummy-url"
endpointProtocol: 5
memberNames: ["C=GB, L=London, O=Member1", "C=GB, L=London, O=Member2"]
```

4. Sample YAML with `members` which all use a common endpoint, and Member1 overrides the protocol version
```yaml
endpoint: "http://dummy-url"
endpointProtocol: 5
members:
    - name: "C=GB, L=London, O=Member1"
      status: "PENDING"
      endpointProtocol: 10
    - name: "C=GB, L=London, O=Member2"
```

# Onboard a member to an existing Corda cluster
This command should only be used for internal development.

This is a sub-command under the `network` plugin for on-boarding a member (MGM or standard member) into a running Corda cluster.

To run the network either use the app simulator `deploy.sh` script ([see here](../../../../../../../../../../../applications/tools/p2p-test/app-simulator/scripts/README.md)) or run a combined worker locally ([see here](../../../../../../../../../../../applications/workers/release/combined-worker/README.md)).

## Onboard an MGM member to an existing Corda cluster
This command should only be used for internal development. See the [wiki](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding) for more details.

This command should only be used for internal development. See
the [wiki](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding) for more details.

This is a sub-command under the `dynamic` sub-command to onboard a new MGM member (and create a new group). By default, the command will save the group policy file into `~/.corda/gp/groupPolicy.json` (and will overwrite any
existing group policy file there).
Use the `--save-group-policy-as` to indicate another location to save the MGM group policy file (that can be used to
create CPIs - [see here](../../../../../../../../../package/README.md))

Examples of on-boarding an MGM can be:

```shell
./corda-cli.sh network dynamic onboard-mgm --x500-name='O=MGM, L=London, C=GB' --user=admin --password=admin --target=https://localhost:8888 --insecure

./corda-cli.sh network dynamic onboard-mgm --cpi-hash=D8AF6080C7B4 --user=admin --password=admin --target=https://localhost:8888 --insecure

./corda-cli.sh network dynamic onboard-mgm --save-group-policy-as /tmp/groupPolicy.json --user=admin --password=admin --target=https://localhost:8888 --insecure

./corda-cli.sh network dynamic onboard-mgm --user=admin --password=admin --target=https://localhost:8888 --p2p-gateway-url=https://localhost:8888 --p2p-gateway-url=https://localhost:8886 --insecure
```

Use the `--help` to view all the other options and defaults.

See [here](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding) for details on how to do it manually.

## Onboard a standard member to an existing cluster

This command should only be used for internal development. See
the [wiki](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks)) for more details.

This is a sub-command under the `dynamic` sub-command to onboard a new member to an existing group.

To decide which CPI to use, there are three options:
* If you know the CPI hash, you can use it with the `--cpi-hash` option
* If you have the CPI file (for example, from the [package command](../../../../../../../../../package/README.md)), you
  can use it with the `--cpi-file` option.
* If you have a CPB and a group policy file (from the `dynamic onboard-mgm` command), you can use the `--cpb-file`
  and `--group-policy-file` option. This will create an unsigned CPI and save it in your home directory.

If you want to wait until your request gets approved/declined you should use the `--wait` option. Although, this is only
advised if you are sure your request will be finalized automatically.
Default value is `false`, so we won't wait by default until your member is fully onboarded. Note that, the MGM might
need to manually approve (or decline) your submitted registration to be fully onboarded by using the `registrationId`.

Few examples of on-boarding a member can be:
```shell
./corda-cli.sh network dynamic member --x500-name='O=Alice, L=London, C=GB' --cpb-file ~/corda-runtime-os/testing/cpbs/chat/build/libs/*.cpb --user=admin --password=admin --target=https://localhost:8888 --insecure
./corda-cli.sh network dynamic member --x500-name='O=Alice, L=London, C=GB' --cpb-file ~/corda-runtime-os/testing/cpbs/chat/build/libs/*.cpb --wait --user=admin --password=admin --target=https://localhost:8888 --insecure
./corda-cli.sh network dynamic member --cpi-file /tmp/calculator.cpi --user=admin --password=admin --target=https://localhost:8888 --insecure
./corda-cli.sh network dynamic member --cpi-hash 200E86176EF2 --user=admin --password=admin --target=https://localhost:8888 --insecure
```
Use the `--help` to view all the other options and defaults.

See [here](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks)) for details on how to do it manually.

# Get Members List

> Use `--help` to see information about commands and available options.

This is a sub-command under the `network` plugin to view the member list via HTTP.

For example,

```shell
./corda-cli.sh network members-list --user=admin --password=admin --target=https://localhost:8888 --insecure members-list -h=<holding-identity-short-hash>
```
