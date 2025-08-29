# cursing_less

![Build](https://github.com/msedgren/cursing_less/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/27057.svg)](https://plugins.jetbrains.com/plugin/27057)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27057.svg)](https://plugins.jetbrains.com/plugin/27057)

<!-- Plugin description -->
This project provides basic support for using IntelliJ with [Talon](https://talonvoice.com/) and more advanced support
of navigation and manipulation using decorators within the editor. Basic support is built on top of and replaces that provided by the
plugin [Voice Code Idea](https://github.com/anonfunc/intellij-voicecode).

This plugin requires [cursing_less_talon](https://github.com/msedgren/cursing_less_talon) and [Talonhub/community](https://github.com/talonhub/community).  
Cursing_less_talon provides details about the usage and available commands. Alternatively, you can disable the decorators within 
the plugin settings and only use the functionality provided by Talonhub/Community.

<!-- Plugin description end -->
## Installation

### Note: this assumes that you have Talon installed and running along with [Talonhub/community](https://github.com/talonhub/community).
1. Install this plugin in IntelliJ. You may build or install from 
the IntelliJ Marketplace (see link above). When building use the following steps:
   1. `./gradlew clean buildPlugin` (Mac or Linux a gradlew.bat is provided for windows)
   2. Browse to Plugins in Settings and click the gear.
   3. Select 'Install Plugin From Disk...'
   4. Browse to the generated plugin jar and select it (cursing_less/build/)
2. Clone [cursing_less_talon](https://github.com/msedgren/cursing_less_talon) to `~/.talon/user`  

