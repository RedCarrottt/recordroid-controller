# Recordroid: Controller Part
Recordroid is a response-aware replaying tool for Android.

The source code of Recordroid is composed of platform part and controller part.
* Platform Part: http://github.com/RedCarrottt/recordroid
* Controller Part: This repository

# Prerequisites
1. Eclipse
1. Java SDK
  * Either Oracle JDK or OpenJDK can be used
1. Android SDK
  * You should set PATH for the platform tools of Android SDK (especially for _adb_).

# How to Build
1. Run Eclipse and import this directory on the Eclipse.
1. Build it in Eclipse!
  * You can also build it with [Apache Ant](http://ant.apache.org/).
1. After the building, you can get _RecordroidController.jar_ file in _out_ directory.
  
# How to Use
1. Connect your target Android device to your host PC with micro-USB cable.
1. Ensure that _adb_ can access your target device.
  1. $ adb devices
1. Run the _RecordroidController.jar_ file with _java_ binary.
  1. $ java -jar out/RecordroidController.jar
1. Recordroid Controller's GUI window will be displayed.
1. Push record button(red circle button) for recording your workload.
1. Make interactions on your target devices.
1. Push replay button(green arrow button) for replaying your workload.

# Dependency
There is no external version dependency for this project.
