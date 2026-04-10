/*
 * InterSego - Bus Timetable App for Segovia
 * Copyright (C) 2025 Bruno Follón
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

# Timeline Indicator Refactoring: From Hardcoded to Adaptive

## Overview

This document details the complete refactoring journey of the timeline indicator component across `NextDepartureScreen` and `DayScheduleScreen`. The refactoring demonstrates a pattern: **identifying hardcoded "magic values," understanding their origin, and replacing them with calculated/adaptive solutions that respond to actual layout dimensions.**

**Commits involved:**
- `e4d9402` - Refactor Canvas to Box-based approach
- `65f21c9` - Fix line connections with fillMaxHeight + offset
- `1517d6f` - Replicate refactor to DayScheduleScreen
- `f05c75a` - Dynamic offset calculation
- `1228f4a` - Fix unit conversion (pixels → dp)

---

## Phase 1: Initial State – Canvas-Based Imperative Drawing

### The Problem

Both `NextDepartureScreen` and `DayScheduleScreen` rendered timeline indicator lines using imperative Canvas drawing:

```kotlin
Canvas(modifier = Modifier
    .width(2.dp)
    .height(56.dp)
    .offset(y = 20.dp)
) {
    drawLine(
        color = Color.LightGray,
        start = Offset(size.width / 2, 0f),
        end = Offset(size.width / 2, size.height),
        strokeWidth = 2.dp.toPx()
    )
}
```

**Issues with this approach:**

1. **Imperative vs Declarative** – Canvas requires low-level drawing APIs, harder to reason about and maintain
2. **Hardcoded dimensions** – `height(56.dp)` and `offset(y = 20.dp)` are "magic numbers" with no clear derivation
3. **Disconnected lines** – Lines didn't connect between rows; gaps appeared where the line ended and the next row began
4. **Fixed height assumption** – Assumes all rows are the same height, fragile if content varies

### Why Hardcoded Values Were Wrong

The `56.dp` and `20.dp` values were chosen empirically to "look right" at design time, but they don't adapt to:
- Variable row heights (different text content, optional fields)
- Different device densities or screen sizes
- Changes to content or spacing logic

**Lesson:** Hardcoded dimension values are often a sign that a calculation should exist but hasn't been implemented yet.

---

## Phase 2: Refactor to Composable Box-Based Approach

### Why This Was Needed

The refactoring to `Box.background()` achieved two goals:

1. **More idiomatic Compose** – Use declarative layout primitives instead of imperative Canvas drawing
2. **Consistency** – Match `RouteStopsScreen`'s approach, which uses Box-based lines and already connects properly

### The Refactoring

Replaced:
```kotlin
Canvas { drawLine(...) }
Surface(shape = extraSmall, color = primary) {}
```

With:
```kotlin
Box(
    modifier = Modifier
        .width(2.dp)
        .fillMaxHeight()
        .offset(y = 35.dp)
        .background(lineColor)
)
Box(
    modifier = Modifier
        .size(12.dp)
        .background(dotColor, CircleShape)
)
```

**Key insight:** `fillMaxHeight()` allows the line to adapt to the row's actual height, but we still have hardcoded `offset(y = 35.dp)`.

---

## Phase 3: Fix Line Connections – The "Shift Down" Problem

### The Issue

Even with `fillMaxHeight()`, lines didn't visually connect between rows. Why?

**Layout anatomy:**
- Each Row has `padding(vertical = 8.dp)` → 8dp gap above and below content
- The line uses `fillMaxHeight()` → extends full row height
- But the line stops at the padding edge, creating a visible gap to the next row's line

### The Solution: Remove Padding + Add Bottom Padding

Instead of symmetrical `padding(vertical = 8.dp)`:
```kotlin
// Before:
Row(modifier = Modifier.padding(vertical = 8.dp))

// After:
Row(modifier = Modifier
    .height(IntrinsicSize.Min)  // Constrain height so children can fillMaxHeight()
    .padding(bottom = 8.dp)       // Spacing only below, line can extend upward
)
```

**Why this works:**
- `height(IntrinsicSize.Min)` forces the Row to measure its actual content height first
- Removing top padding allows the line to extend from the previous row's padding area
- Bottom padding still provides visual breathing room
- Lines now visually connect because they span edge-to-edge of their Row containers

**Conceptual lesson:** Sometimes the bug isn't the component, it's the container's layout properties. Layout decisions propagate downward to children.

---

## Phase 4: Dynamic Offset Calculation – The Breakthrough

### The Realization

After getting line connections to work with the hardcoded `offset(y = 35.dp)`, the natural question arose:

> "Why is 35dp correct? Can we calculate it instead of hardcoding it?"

### The Conceptual Shift

**Old thinking:** "The line needs to be offset by 35dp because that's what looks right."

**New thinking:** "The line should start from the bottom of the centered dot. So offset = containerHeight/2 + dotSize/2."

This is a fundamental shift from **empirical tuning** to **calculated positioning**.

### The Implementation

```kotlin
var containerHeight by remember { mutableStateOf(0) }

Box(
    modifier = Modifier
        .fillMaxHeight()
        .onSizeChanged { size ->
            containerHeight = size.height  // Measure actual container
        },
    contentAlignment = Alignment.Center
) {
    if (!isLast && containerHeight > 0) {
        // Calculate offset based on actual dimensions
        val lineOffsetDp = containerHeight / 2 + dotSize / 2
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(y = lineOffsetDp)
                .background(lineColor)
        )
    }
}
```

**Key techniques:**
- **`onSizeChanged`** – Measure the Box after composition to get actual layout dimensions
- **State variable** – Store measured height for recalculation
- **Guard condition** – Only render line after measurement (`containerHeight > 0`)

---

## Phase 5: Fix Unit Mismatch – The Final Bug

### The Problem

After implementing dynamic offset calculation, the line was positioned way too far down. Debugging revealed:

- Container height reported as `153`
- Expected container height: `~50dp`
- **Issue:** 153 was in pixels, not dp!

`onSizeChanged` returns **pixels**, not dp. The calculation was mixing units:
```kotlin
// WRONG: treating pixels as dp
val offset = (containerHeight / 2 + dotSize / 2).dp
//           153 pixels treated as 153 dp → way too large!
```

### The Solution: Explicit Unit Conversion

```kotlin
val density = LocalDensity.current

// Convert pixels to dp using device density
val containerHeightDp = with(density) { containerHeight.toPx() }

// Now all calculations are in consistent units
val lineOffsetDp = containerHeightDp / 2 + dotRadiusDp
```

**Why this matters:**
- Android reports measurements in **pixels** (device-dependent)
- Compose works in **dp** (density-independent pixels)
- Mixing units leads to incorrect calculations
- Explicit conversion makes the code's intent clear

### The Math Check

With a typical smartphone (density ≈ 3):
- Container height: 153 pixels = 51 dp
- Offset: 51/2 + 6 = 31.5 dp ✓ (matches empirical 35 dp closely)

---

## Conceptual Pattern: Hardcoded → Calculated → Adaptive

This refactoring follows a three-stage pattern that appears throughout UI development:

### Stage 1: Hardcoded Values
```kotlin
.offset(y = 35.dp)  // "Magic number"
.height(56.dp)      // "Magic number"
```

**Characteristics:**
- Simple and immediate
- Doesn't scale to variations
- No clear derivation
- Hard to maintain

**Warning signs:**
- Comments like "offset needs to be..." or "this number was tuned to..."
- Values that don't align with other dimensions in the design system
- Layout breaking when content changes

### Stage 2: Calculated Values
```kotlin
val offset = containerHeight / 2 + dotSize / 2
.offset(y = offset)
```

**Characteristics:**
- Derives values from knowable quantities
- Scales to different row heights
- Self-documenting intent
- Still requires measurement/state

### Stage 3: Adaptive/Responsive Values
```kotlin
// Measure actual dimensions, recalculate if needed
.onSizeChanged { size -> containerHeight = size.height }
.offset(y = calculateOffset())
```

**Characteristics:**
- Responds to actual layout
- No assumptions about fixed sizes
- Works across device densities and content variations
- Most maintainable long-term

---

## Key Lessons for Future Refactoring

### 1. Question Hardcoded Numbers
When you see a number like `35.dp` or `56.dp`:
- Ask: "Where does this value come from?"
- Ask: "What would break if this changed?"
- Ask: "Can this be calculated?"

### 2. Watch for Layout Assumption Bugs
Many layout bugs stem from wrong container properties propagating to children:
- Padding/margin assumptions
- Height/width constraints
- Alignment settings

When lines/elements don't align as expected, check the **parent's** layout properties first.

### 3. Understand Unit Semantics
Be explicit about units:
- **Pixels** – Device-dependent, from `onSizeChanged`, canvas operations
- **Dp** – Density-independent, Compose modifiers
- **Em/Sp** – Typography units

**Always convert explicitly** when mixing sources.

### 4. Use Measurement as Source of Truth
Instead of guessing dimensions:
```kotlin
// Less maintainable:
.height(80.dp)
.offset(y = 20.dp)

// More maintainable:
.onSizeChanged { actualHeight = it.height }
.offset(y = actualHeight / 2 + dotSize / 2)
```

Measurement-based layouts adapt to any content automatically.

### 5. Extract Measurements into State
When you measure a composable:
```kotlin
var measuredHeight by remember { mutableStateOf(0) }
.onSizeChanged { size -> measuredHeight = size.height }
```

This allows:
- Recalculation when state changes
- Debugging (you can log the measured values)
- Reuse across multiple calculations

---

## Before and After

### Before (Hardcoded, Disconnected)
```
Row 1: [dot] ← line offset(20) height(56)
       ___
Gap (padding)
Row 2: [dot] ← line offset(20) height(56)
       ___
Gap (padding)
Row 3: [dot] ← line offset(20) height(56)
```

Lines don't visually connect; gaps are visible.

### After (Calculated, Connected)
```
Row 1: [dot] ← line fillMaxHeight() offset(25)
───────────
Row 2: [dot] ← line fillMaxHeight() offset(25)
───────────
Row 3: [dot] ← line fillMaxHeight() offset(25)
───────────
```

Lines visually connect; offset adapts to actual row height.

---

## Code Inspection Checklist for Future Refactoring

When hunting for hardcoded-value bugs in the codebase:

- [ ] Search for numeric literals without clear purpose (`.offset(y = 35.dp)`)
- [ ] Look for fixed heights/widths that don't match content (`.height(56.dp)`)
- [ ] Check if measurements could be calculated from design system values
- [ ] Verify unit conversions when mixing pixels and dp
- [ ] Test with varying content lengths (short, long, multilingual)
- [ ] Test on different device densities (if possible)
- [ ] Look for layout properties that might be constraining children
- [ ] Ask: "What would break if the content changed?"

---

## Commits to Review

```
e4d9402 - refactor(Android): replace Canvas-based timeline with composable Box approach
65f21c9 - fix(Android): properly connect timeline indicator lines with fillMaxHeight + offset
1517d6f - refactor(Android): apply timeline indicator refactor to DayScheduleScreen
f05c75a - refactor(Android): use dynamic offset calculation for timeline lines
1228f4a - fix(Android): convert pixels to dp in timeline indicator offset calculation
```

Each commit represents one step of the conceptual journey from hardcoded to calculated to measured/adaptive.

---

## Related Patterns in InterSego

This refactoring pattern appears in other places:

1. **Stop cluster positioning** – Dynamic offset calculation in M6/M7 parsers based on actual coordinate values
2. **Reminder lead times** – Calculated from departure time, not hardcoded
3. **Cache invalidation** – Parser version-based, not hardcoded timestamps

Look for similar opportunities to replace magic numbers with calculations that adapt to actual data.
