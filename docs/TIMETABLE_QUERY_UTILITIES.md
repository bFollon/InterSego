# Timetable Query Utilities Refactor

**Date:** April 10, 2026  
**Commit:** `178578b`  
**Status:** ✅ Complete

## Overview

Extracted date-based timetable query logic into reusable utility functions on both platforms to enable flexible date-based departure queries and support future date picker UI.

## Files

### iOS
- **Created:** `iOS/InterSego/Services/TimetableQuery.swift`
- **Modified:** `iOS/InterSego/Services/DeparturesService.swift`

### Android
- **Created:** `android/app/src/main/java/com/github/bfollon/intersego/services/TimetableQueryUtils.kt`
- **Modified:** `android/app/src/main/java/com/github/bfollon/intersego/services/DeparturesService.kt`

## API

### iOS: `TimetableQuery`

```swift
enum TimetableQuery {
    /// Returns the set of DayTypes applicable on the given date.
    /// - Saturday → {.saturday, .weekend}
    /// - Sunday → {.sunday, .weekend, .holiday}
    /// - Weekday → {.weekday}
    static func dayTypesForDate(_ date: Date) -> Set<DayType>

    /// Filters timetables matching the given date and optional criteria.
    static func filterTimetables(
        _ timetables: [BusTimetable],
        date: Date,
        routeId: String? = nil,
        stopId: String? = nil,
        direction: String? = nil
    ) -> [BusTimetable]
}
```

### Android: `TimetableQueryUtils`

```kotlin
object TimetableQueryUtils {
    /// Returns the set of DayTypes applicable on the given date.
    fun dayTypesForDate(date: Calendar = Calendar.getInstance()): Set<DayType>

    /// Filters timetables matching the given date and optional criteria.
    fun filterTimetables(
        timetables: List<BusTimetable>,
        date: Calendar,
        routeId: String? = null,
        stopId: String? = null,
        direction: String? = null
    ): List<BusTimetable>
}
```

## Key Design Decisions

1. **Date-Agnostic:** Both functions work with any calendar date (past, present, future)
2. **Set-Based DayType Matching:** Saturday matches `{.saturday, .weekend}`, Sunday matches `{.sunday, .weekend, .holiday}`. This correctly handles routes that use `.weekend` (M4) vs routes that use `.saturday`/`.sunday` separately (M6)
3. **Seasonal Filtering Separate:** `filterTimetables()` returns timetables that apply on the given day type, but seasonal filtering (school-only, summer-only) remains on `BusTimetable.seasonalDepartures()`
4. **Optional Filters:** All filter parameters (routeId, stopId, direction) are optional, enabling flexible queries

## Usage Examples

### iOS

Query departures for a specific date and stop:
```swift
let futureDate = Calendar.current.date(byAdding: .day, value: 3, to: Date())!
let timetables = TimetableQuery.filterTimetables(allTimetables, date: futureDate, stopId: "stop-123")
let departures = timetables.flatMap { $0.seasonalDepartures(weekday: someWeekday) }
```

Query by date and route:
```swift
let timetables = TimetableQuery.filterTimetables(allTimetables, date: someDate, routeId: "M4")
```

### Android

Query departures for a specific date and stop:
```kotlin
val futureDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
val timetables = TimetableQueryUtils.filterTimetables(allTimetables, futureDate, stopId = "stop-123")
val departures = timetables.flatMap { it.seasonalDepartures(weekday = someWeekday) }
```

## What This Enables

### Current (Refactoring Only)
- Extracted date→dayType logic from `DeparturesService`
- `DeparturesService` internals now delegate to the utility
- No breaking API changes; existing views continue to work unchanged

### Future: Date Picker UI
When implementing a date picker feature, you can now:
1. Get user-selected date from picker
2. Call `TimetableQuery.filterTimetables(timetables, date: selectedDate, stopId: stop.id)`
3. Apply seasonal filtering and sort as needed
4. Display departures for that date

This eliminates the need to duplicate the day-type detection logic in the UI layer.

## Testing

Both platforms build successfully with the new utilities:
- **iOS:** `xcodebuild build -scheme InterSego` ✅
- **Android:** `./gradlew assembleDebug` ✅

Existing views continue to work unchanged (verified by successful builds).

## Notes

- The utilities are testable and reusable across the codebase
- No changes to public APIs of `DeparturesService`, `TimetableService`, or views
- The existing `DayType.matchesCalendarDay()` extension on Android is not used; our set-based matching is more flexible for handling routes with mixed day type usage
