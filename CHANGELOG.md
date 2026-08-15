## v0.4.0 - 2026-08-15

### Improvements
- Loops: relative "next run" labels now tick with the clock instead of freezing at the time the crontab was loaded
- Give each CLI one place that declares its identity and file layout, so detection, scanning and creation stay in step

### Fixes
- Look for the Grok and OpenCode binaries in `/usr/local/bin` as well
- Recognise `.opencode.json` as a project marker during project discovery

## v0.3.0 - 2026-08-03

### Features
- Browse, create and edit project and system rules (`.claude/rules`, `.grok/rules`, `.cursor/rules`, `.agent/rules`)
- List Cursor's legacy `.cursorrules` alongside the other agent root files

## v0.2.0 - 2026-08-02

### Features
- Add support for Codex, Cursor and Gemini CLI agents

### Improvements
- Unify agent abstractions for cleaner multi-CLI handling
- Improve start-up performance
- Polish UI
- Move Homebrew publishing to the dedicated tap

## v0.1.0 - 2026-08-01

### Features
- First release: browse and edit skills, memories, agent files and config for Claude, Grok and OpenCode
- Loops: schedule recurring agent-CLI runs through the user crontab
