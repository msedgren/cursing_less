# Project Guidelines for Cursing_Less

## Project Overview
Cursing_Less is an IntelliJ IDEA plugin that enhances coding with voice recognition through [Talon](https://talonvoice.com/). The plugin provides visual decorators in the editor to facilitate navigation and code manipulation using voice commands. It's designed to replace and improve upon the functionality provided by the Voice Code Idea plugin.

## Project Structure
- `src/main/kotlin/org/cursing_less/` - Main source code
  - `service/` - Core services including markup, preferences, and color/shape management
  - `listener/` - Event listeners for application and editor events
  - `handler/` - Custom handlers for editor actions
  - `renderer/` - Rendering components for visual decorators
  - `color_shape/` - Classes for managing colors and shapes used in decorators
  - `settings/` - Plugin configuration UI and settings
- `src/main/resources/` - Resource files including plugin.xml and messages
- `src/test/` - Test classes

## Key Components
1. **CursingMarkupService** - Manages the visual decorators in the editor
2. **ColorAndShapeManager** - Handles the assignment of colors and shapes to tokens
3. **CursingPreferenceService** - Manages user preferences for the plugin
4. **CursingApplicationListener** - Handles application lifecycle events
5. **CursingCaretListener** - Responds to caret movements to update decorators

## Development Guidelines
1. **Testing**: When making changes, run the relevant tests to ensure functionality is preserved. Use `run_test` to execute tests for modified components.
2. **Building**: The project uses Gradle for building. Run `./gradlew clean buildPlugin` to build the plugin.
3. **Code Style**: Follow Kotlin coding conventions and maintain the existing code style.
4. **Dependencies**: The plugin requires [cursing_less_talon](https://github.com/msedgren/cursing_less_talon) and [Talonhub/community](https://github.com/talonhub/community) to function properly.

## Testing
When implementing changes, run the relevant tests to verify functionality:
```
run_test src/test/kotlin/org/cursing_less/service/CursingColorShapeLookupServiceTest.kt
```

## Building
Before submitting changes, build the project to ensure it compiles correctly:
```
./gradlew clean buildPlugin
```

## Additional Resources
- [Talon Voice](https://talonvoice.com/) - The voice recognition system this plugin integrates with
- [cursing_less_talon](https://github.com/msedgren/cursing_less_talon) - Companion Talon scripts for this plugin
- [Talonhub/community](https://github.com/talonhub/community) - Community Talon scripts required by this plugin
