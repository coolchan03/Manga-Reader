/*
 * Derived from Mangayomi (https://github.com/kodjodevf/mangayomi), Apache License 2.0.
 * File: lib/eval/javascript/preferences.dart
 * This is the JavaScript half of Mangayomi's extension runtime, kept as close to the original as
 * possible so that unmodified Mangayomi JS extensions run. The host half is implemented in Kotlin.
 */
class SharedPreferences {
    get(key) {
        return sendMessage(
            "get",
            JSON.stringify([key])
        );
    }
    getString(key, defaultValue) {
        return sendMessage(
            "getString",
            JSON.stringify([key, defaultValue])
        );
    }
    setString(key, defaultValue) {
        return sendMessage(
            "setString",
            JSON.stringify([key, defaultValue])
        );
    }
}
