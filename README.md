# cursing_less

![Build](https://github.com/msedgren/cursing_less/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
This project to provides basic support for using IntelliJ with [Talon](https://talonvoice.com/) and more advanced support
of navigation and manipulation using decorators on text file. Basic support is built on top of and replaces that provided by the
plugin [Voice Code Idea](https://github.com/anonfunc/intellij-voicecode).

This plugin requires [cursing_less_talon](https://github.com/msedgren/cursing_less_talon).
Please see this for usage instruction.
<!-- Plugin description end -->
## Installation

### Note: this assumes that you have Talon installed and running along with [Talonhub/community](https://github.com/talonhub/community).
1. Install this plugin in IntelliJ. You may build or, when available, install from 
the IntelliJ Marketplace. When building use the following steps:
   2. `./gradlew clean buildPlugin` (Mac or Linux a gradlew.bat is provided for windows)
   3. Browse to Plugins in Settings and click the gear.
   4. Select 'Install Plugin From Disk...'
   5. Browse to the generated plugin jar and select it (cursing_less/build/)
2. Clone [cursing_less_talon](https://github.com/msedgren/cursing_less_talon) to `~/.talon/user`  

