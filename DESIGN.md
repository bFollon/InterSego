---
name: InterSego
description: Bus timetable app for Segovia, Spain — native Material 3 on Android, native SwiftUI/HIG on iOS
colors:
  transit-blue: "#1976D2"
  transit-blue-light: "#42A5F5"
  route-orange: "#FF9800"
  route-orange-light: "#FFB74D"
  success-green: "#4CAF50"
  warning-orange: "#FFA726"
  warning-orange-text: "#8B5E00"
  confirmation-green: "#34C759"
  brand-icon-green: "#3CA27A"
  alert-critical: "#D32F2F"
  alert-warning: "#FF9800"
  alert-info: "#1976D2"
typography:
  display:
    fontFamily: "System default (Roboto on Android, SF Pro on iOS)"
    fontSize: "Material displayLarge/HIG Large Title — role-mapped, not custom"
    fontWeight: 400
  body:
    fontFamily: "System default (Roboto on Android, SF Pro on iOS)"
    fontSize: "16sp / 17pt"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0.5sp"
  label:
    fontFamily: "System default"
    fontSize: "Material labelMedium/HIG Footnote-Caption — role-mapped"
    fontWeight: 600
rounded:
  card-android: "20dp"
  card-ios: "20pt"
  pill: "18dp/pt"
  compact-card: "16dp/pt"
  chip: "8dp/pt"
  pill-full: "50dp/pt"
spacing:
  card-padding: "18dp/pt"
  pill-padding-v: "16dp/pt"
  pill-padding-h: "8dp/pt"
  section-gap: "8dp/pt"
components:
  square-landing-card:
    backgroundColor: "{colors.transit-blue}"
    rounded: "{rounded.card-android}"
    padding: "18dp"
  pill-landing-card:
    backgroundColor: "transparent tint over {colors.transit-blue}"
    rounded: "{rounded.pill}"
    padding: "16px 8px"
---

# Design System: InterSego

## Overview

**Creative North Star: "The Reliable Timetable"**

InterSego reads as trustworthy public infrastructure, not a branded consumer product. Its color, motion, and density budget stay conservative and civic — the personality lives in restraint and reliability, not in flourish. The quality bar is iOS's own polish (generous spacing, quiet confidence, soft depth) — but that bar is translated into each platform's native idiom, never ported wholesale. Android is genuinely Material 3 (Roboto, Material color roles, tonal elevation, Material You dynamic color); iOS is genuinely SwiftUI/HIG (SF Pro, semantic system colors, safe-area-respecting navigation, soft ambient shadows). A component that looks identical on both platforms by coincidence of matching values is fine; a component that looks identical because one platform copied the other's control is a bug.

The app's live interactive color today is **transit blue** (#1976D2 Android / ~#1975D3 iOS accent), not the brand's icon green (#3CA27A) — see the Named Rule under Colors. This document records what is actually implemented, not an aspirational palette.

**Key Characteristics:**
- Civic-blue primary, low-saturation secondary orange for route/schedule emphasis
- Flat and tonal on Android (Material 3 default: no drop shadows, borders carry separation); soft ambient shadow on iOS (HIG default)
- Default system typography on both platforms — no custom brand typeface
- Generous rounded corners (18–20dp/pt) on card-shaped surfaces; more clipped shapes (8dp/pt) on chips/badges
- Confirmation/success states get their own accent (iOS-system green #34C759), distinct from both the transit-blue interactive color and the brand-icon green

## Colors

Palette is functional and low-key: one interactive primary (blue), one secondary for route/schedule emphasis (orange), and situational accents for success/warning/error/severity states. There is no decorative or purely brand-expressive color beyond the app icon.

### Primary
- **Transit Blue** (`#1976D2` Android `primary` / light scheme; `#42A5F5` dark scheme; iOS `AccentColor` ≈ `#1975D3`): the single interactive/tint color driving buttons, links, selected states, active pills, and icons across both platforms. On Android 12+, this is overridden by Material You dynamic color derived from the user's wallpaper when enabled — blue is the *static fallback*, not always the rendered value.

### Secondary
- **Route Orange** (`#FF9800` light / `#FFB74D` dark): route/schedule highlighting — Material `secondary`/`tertiary` role on Android; used more sparingly on iOS, mostly for warning-adjacent badges.

### Neutral
- Android: Material 3 default neutral/surface roles (`surface`, `surfaceVariant`, `onSurface`, `onSurfaceVariant`, `outlineVariant`) — no custom neutral scale defined; the app inherits M3's tonal neutrals as-is.
- iOS: system semantic colors (`systemBackground`, `secondaryLabel`, `systemGray4`/`systemGray5`) — no custom neutral scale.

### Situational accents
- **Success Green** (`#4CAF50`): Material semantic success color, defined but used narrowly.
- **Confirmation Green** (`#34C759`, iOS-system green): the color actually rendered for "confirmed"/"boarding confirmed" states on the Landing screen's square/pill cards on Android — an iOS-system value used inside an otherwise-Material surface.
- **Warning Orange** (`#FFA726`) / **Warning Orange Text** (`#8B5E00`): warning banners and their on-light-background text.
- **Service alert severity**: critical = red (`#D32F2F`-class), warning = orange (`#FF9800`), info = blue (`#1976D2`) — the alert pill's color-coding reuses the same interactive blue for its lowest severity tier.
- **Brand Icon Green** (`#3CA27A`): appears in exactly two places in the codebase — the splash/launch icon rendering and one accent dot on the About screen. It is the app's marketing/store-listing brand color but is **not** the app's interactive UI color.

### Named Rules
**The Icon-Is-Not-The-Theme Rule.** `#3CA27A` is the brand's external identity (app icon, store listing) and is confirmed as binding. It is not currently the app's UI primary — that's transit blue. Do not silently "fix" this by recoloring the primary theme to green; that's a deliberate redesign decision the user has not made, not a documentation correction. Treat the coexistence as documented drift.

**The One Confirmation Color Rule.** Success/confirmed states should converge on one green. Today there are three candidates in the codebase (`#4CAF50` Material success, `#34C759` iOS-system, `#3CA27A` brand icon) and only `#34C759` is actually wired to a real confirmation UI. New confirmation-state work should not introduce a fourth.

## Typography

**Font:** System default only — Roboto (Android, via Material 3's default `FontFamily.Default`), San Francisco/SF Pro (iOS, via SwiftUI's default `Font`). No custom or brand typeface is loaded on either platform.

**Character:** Plain, legible, unbranded. Type carries no personality of its own — hierarchy and restraint do the work instead.

### Hierarchy
- **Body** (Regular 400, 16sp/24sp line-height, 0.5sp tracking on Android; 17pt HIG Body on iOS): the only text style explicitly overridden in the Android theme (`Type.kt`); every other Material role (display/headline/title/label) uses the unmodified M3 default scale. iOS uses unmodified Dynamic Type text styles throughout — no hard-coded point sizes were found.
- **Title/Label roles**: Android composables reach into `MaterialTheme.typography.titleMedium`/`labelMedium` directly (with occasional inline `fontSize` overrides, e.g. 18sp on landing card titles, 13sp on subtitles) rather than defining new named styles.

### Named Rules
**The No-Custom-Font Rule.** Neither platform loads a brand typeface. Introducing one on only one platform would break the "same reliability, native idiom" premise — a font choice, if ever made, must be evaluated on both platforms together.

## Layout

Both platforms share the same compositional pattern on the Landing screen — a full-width primary action, a 2-up grid of square cards, and a 3-up row of compact pills — but implement it with platform-native layout primitives (Compose `Column`/`Row` with `Arrangement`/`weight` on Android; SwiftUI `HStack`/`VStack` with `.frame`/spacers on iOS), not a shared layout engine.

Spacing is dense-but-breathing: card internal padding sits around 18dp/pt, pill padding around 16dp/pt vertical × 8dp/pt horizontal, inter-element gaps around 8dp/pt. No formal spacing scale/token system exists on either platform — values are set per-composable/per-view, so exact spacing can drift slightly between analogous Android and iOS screens; treat 8/16/18/20 as the observed rhythm, not a hard-coded scale.

Android additionally must honor edge-to-edge window insets (status bar, nav bar, IME) and adapt navigation (bottom bar → rail/drawer) at expanded widths if the app ever ships to larger Android form factors — not yet exercised, since the app currently targets phones. iOS lays out inside safe-area insets with large titles collapsing to inline on scroll where used (e.g. detail screens).

## Elevation & Depth

The two platforms deliberately diverge here, matching each OS's own convention rather than unifying:

- **Android is flat and tonal**, per Material 3 default. Cards and pills use a 1dp border in `outlineVariant`/tint-derived colors for separation, not a drop shadow. Where elevation values do appear (`Card`/`Button` `elevation` params), they're small (1–8dp) and used sparingly, not as a primary depth language.
- **iOS uses soft ambient shadow.** Cards, tutorial sheets, and selectable rows carry a `.shadow(color: .black.opacity(0.08–0.12), radius: 4–20, y: 2–5)` — always soft and low-opacity, never a hard/high-contrast shadow. Larger radii (20) appear on modal/splash-adjacent surfaces; the everyday card shadow is closer to `radius: 4, y: 2` at ~8–10% black.

### Named Rules
**The Native Depth Rule.** Depth conveys through borders/tonal surfaces on Android and through soft shadow on iOS. Never introduce a drop shadow on Android to "match iOS," and never flatten iOS cards to borders-only to "match Android" — the platforms are allowed to look different here on purpose.

## Shapes

Rounded corners dominate; there are no sharp-cornered surfaces in the UI. Two tiers recur across both platforms:

- **20dp/pt** — the primary card radius (square landing action cards, larger content cards).
- **16–18dp/pt** — secondary surfaces: pills, compact/tutorial cards.
- **8–12dp/pt** — small chips, badges, and tighter controls.
- **50dp/pt (fully rounded/pill-shaped)** — a handful of true pill/capsule shapes (badges, the compact action row).

No sharp corners, no asymmetric/cut corners, no Material 3 Expressive shape-morphing — corner radius is a fixed, static value per surface type on both platforms.

## Components

### Landing action cards (signature component)
The app's most distinctive UI pattern: a configurable landing layout built from two interchangeable card shapes.
- **Square card** (`SquareLandingCard` / `SquareCard`): 1:1 aspect ratio, 20dp/pt corner radius, top-left icon, bold title + lighter subtitle stacked bottom-left, tinted background at low opacity over the interactive color, 1px border in a matching low-opacity/outline tone. Android: flat, border-only separation. iOS: adds the platform's soft ambient shadow.
- **Pill card** (`PillLandingCard` / `PillCard`): centered icon above a max-2-line label, 18dp/pt corner radius, same tint/border language as the square card but compact and centered rather than left-aligned.
- Both card shapes carry a special "confirmed" visual state (icon/label swap + color shift to the confirmation-green accent) for the boarding-confirmation action specifically — the only place a third accent color is allowed to appear on an otherwise blue-tinted surface.

### Buttons
- **Android:** Material 3 filled/tonal/outlined/text buttons per Material's own rules — no custom button component found; standard `Button`/`OutlinedButton`/`TextButton` composables styled through the theme's color scheme.
- **iOS:** platform-standard SwiftUI buttons and tappable rows styled via `.accentColor`/`.tint`, not custom button views.

### Banners / alert pills
Severity-coded compact pill (service alerts) and full-width colored banner (Festivo/holiday notice) — same visual language on both platforms: tinted background, colored left accent or icon, short label text, tap-to-expand-detail where applicable.

### Navigation
- **Android:** Top app bar for screen context; system predictive Back honored; no bottom navigation bar currently — navigation is stack-based from Landing.
- **iOS:** Navigation stack with large titles collapsing to inline on scroll for detail screens; sheets for self-contained tasks (tutorials, About, favorites); left-edge swipe-back preserved.

## Do's and Don'ts

### Do:
- **Do** keep Android on Material 3 components (filled/tonal/outlined buttons, Material dialogs, M3 color roles) and iOS on native SwiftUI/HIG controls (system pickers, sheets, alerts, swipe actions) — the shared "look" comes from matching *values* (radius, spacing, color), not shared *components*.
- **Do** use the 20dp/pt / 18dp/pt / 8–12dp/pt corner-radius tiers when adding new card/pill/chip surfaces, so new UI doesn't introduce a fourth radius value.
- **Do** keep Android depth flat/tonal (borders, not shadows) and iOS depth as soft ambient shadow (`opacity ≤ 0.12`, never hard-edged) — this divergence is intentional, not drift to fix.
- **Do** treat transit blue (`#1976D2`/`~#1975D3`) as the documented interactive primary until a deliberate rebrand decision says otherwise.

### Don't:
- **Don't** port an iOS control onto Android (e.g. a segmented-control look-alike, Cupertino-style switches) or a Material control onto iOS (e.g. a FAB, a Material bottom sheet) to chase visual parity — native-per-platform is a hard constraint (see PRODUCT.md).
- **Don't** introduce Material 3 Expressive shape-morphing, the expressive type scale, or expressive motion into Android without a deliberate decision — the current Android implementation is plain default Material 3, and this document does not commit to Expressive.
- **Don't** add a fourth "success/confirmed" green. Reuse `#34C759` (the one actually wired to a confirmation UI today) unless a rebrand decision consolidates all three greens deliberately.
- **Don't** recolor the app's primary/accent to brand-icon green (`#3CA27A`) as a side effect of an unrelated task — that's a scoped rebrand decision for the user to make explicitly, not an incidental fix.
