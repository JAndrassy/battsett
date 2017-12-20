## BattSett - battery settings

BattSett is a Java command line program for setting battery charging and discharging limits of the Fronius Symo Hybrid. The settings are read and applied using Modbus TCP SunSpec registers of the Symo Datamanager.

To use the program, you must enable Modbus TCP in Symo Datamanager web interface. In Settings -> Modbus, set "tcp" and "Inverter control via Modbus".

The program is released as a java [jar file](https://github.com/jandrassy/battsett/releases/latest).
It can be executed on the command line with Java 8:

`java -jar battsett.jar`

without arguments it prints out the 'usage' help:

```
usage: battset.jar <host>[:<port>] in|i|out|o [0%|<limit>[%]] [enable|1|disable|0]
       battset.jar <host>[:<port>] reserve|r [<minimum>[%]]
```

arguments:
* host - IP address or hostname of Symo Hybrid
* port - Modbus port, default is 502
* in or i - to set charging limit
* out or o - to set discharging limit
* limit - percentage of maximum power for charging and discharging of the batteries
* enable or 1 - to enable the limit
* disable or 0 - to disable the limit
* reserve or r - to set minimum reserve for battery
* minimum - minimum reserve for battery as a percentage of the nominal maximum storage

If argument is only the host, battsett only prints out current settings.

Enabling or disabling the limit without specifying the limit value does not change the limit value. Setting the limit value without specifying enable/disable, enables the limit. Specifying only in or out, disables the limit.

**Use at your own risk.**

### Examples:
```
>java -jar battsett.jar 192.168.1.107
Charge limit 100% is disabled
Discharge limit 100% is disabled
Minimum reserve 0% of the nominal maximum storage
```
Disable discharging:
```
>java -jar battsett.jar 192.168.1.107 out 0%
Charge limit 100% is disabled
Discharge limit 0% is enabled 
Minimum reserve 0% of the nominal maximum storage
```
Disable discharge limit without changing the value:
```
>java -jar battsett.jar 192.168.1.107 out disable
Charge limit 100% is disabled
Discharge limit 0% is disabled
Minimum reserve 0% of the nominal maximum storage
```
Reduce charging:
```
>java -jar battsett.jar 192.168.1.107 in 55
Charge limit 55% is enabled
Discharge limit 0% is disabled
Minimum reserve 0% of the nominal maximum storage
```
Disable charge limit without changing the value:
```
>java -jar battsett.jar 192.168.1.107 in disable
Charge limit 55% is disabled
Discharge limit 0% is disabled
Minimum reserve 0% of the nominal maximum storage
```
Setting the value and disabling the limit:
```
>java -jar battsett.jar 192.168.1.107 in 100% disable
Charge limit 100% is disabled
Discharge limit 0% is disabled
Minimum reserve 0% of the nominal maximum storage
```
Setting a reserve:
```
>java -jar battsett.jar 192.168.1.107 reserve 50%
Charge limit 100% is disabled
Discharge limit 100% is disabled
Minimum reserve 50% of the nominal maximum storage
```
