# Xcode Project Generator

## Problem

`project.pbxproj` explicitly enumerates every source file. Files created outside Xcode (via Claude Code, terminal, etc.) are not auto-registered — they exist on disk but are invisible to the compiler until manually added to the project file. This causes build errors every time new Swift files are added programmatically.

## Solution: adopt a project generator

Both tools let you define glob-based source patterns so new files are picked up automatically on regeneration. The `.xcodeproj` becomes a derived artifact.

### Option A — XcodeGen (lighter lift)

YAML spec, minimal setup. Add a `project.yml` at the repo root or in `iOS/`.

```yaml
name: InterSego
targets:
  InterSego:
    type: application
    platform: iOS
    sources:
      - path: InterSego
        type: group
```

Install: `brew install xcodegen`  
Regenerate: `xcodegen generate` (from `iOS/`)

### Option B — Tuist (better long-term DX)

Swift-based spec, richer feature set, built-in dependency management.

```swift
// Project.swift
let project = Project(
    name: "InterSego",
    targets: [
        .target(
            name: "InterSego",
            destinations: .iOS,
            product: .app,
            sources: ["InterSego/**"]
        )
    ]
)
```

Install: `brew install tuist`  
Regenerate: `tuist generate`

## Recommendation

**XcodeGen** is the lower-friction starting point — the YAML mirrors the existing project structure closely and migration is mostly mechanical. Tuist is better if you later want workspace management, dependencies (SPM/CocoaPods), or templating.

Either way, commit the spec file and gitignore `*.xcodeproj` (or at minimum `project.pbxproj`).
