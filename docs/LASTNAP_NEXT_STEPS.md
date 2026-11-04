# LastNap — saved implementation plan

Branch: `feature/extensions-foundation`

This file preserves the agreed product requirements and implementation order so work can continue without relying on a chat summary. Do not merge to `master` without the user's explicit permission.

## Product requirements

### 1. Custom tool creator skill
- Add a built-in Agent Skill named `tool-creator` alongside `skill-creator`.
- It must appear in the Extensions → Agent Skills list and be disabled until the user enables it for an assistant.
- It teaches the assistant how to design, create, configure, test, edit, and troubleshoot custom tools in the existing Tool Builder.
- Do not inject its instructions globally. Only assistants with the skill enabled may load/use it.

### 2. Assistant-specific custom tools
- The user chooses which custom tools are available to each assistant.
- Tool availability must be stored on the assistant, not inferred by the model.
- A coding assistant may have `tool-creator` and selected tools; a roleplay assistant should not receive them unless the user explicitly assigns them.
- Do not silently enable a tool or skill for every assistant.
- Keep the existing user-level tool definitions in Settings; assistant configuration stores selected tool IDs.

### 3. Tool Builder modes
- Add clear **Simple** and **Advanced** choices.
- Simple mode: user describes the desired tool in ordinary language; the assistant helps produce the tool name, description, input shape, implementation, and test example. The user should not need to hand-write JavaScript or JSON.
- Advanced mode: expose the current technical editor, implementation code, JSON test input, test results, and permission settings.
- Both modes create the same underlying custom-tool format and use the same sandbox, approval rules, and test-before-enable protection. Simple mode must not be a weaker or separate tool system.

### 4. Responsiveness
- Investigate the reported roughly two-second delay when navigating between screens.
- Profile a release build and trace navigation/composition, main-thread work, startup work, and avoidable blocking I/O before changing code.
- Do not claim the delay is fixed without measurements on a real device or a reproducible benchmark.
- Avoid speculative broad rewrites or adding work to every screen.

### 5. Launcher icon
- Use the existing adaptive launcher resource so Android can apply the foreground/background/monochrome layers.
- Preserve the user's supplied artwork. Do not redraw, replace, or regenerate it without permission.

## Implementation and verification order

1. Keep all changes on `feature/extensions-foundation`; never modify or merge `master`.
2. Add and validate the built-in `tool-creator` skill.
3. Add assistant-specific custom-tool selection and filter runtime tool creation by that assistant's selected IDs. Include migration-safe defaults and tests.
4. Add Simple/Advanced Builder modes while preserving the existing sandbox and test-before-enable checks.
5. Correct the launcher resource reference without replacing the supplied art.
6. Run unit tests, Android lint, and debug APK assembly in GitHub Actions. Inspect logs and artifacts; report each result accurately.
7. Investigate navigation performance separately with a release/profile-capable workflow. Do not present an unmeasured optimization as a fix.

## Safety and UX constraints

- User controls assignment of skills and tools for each assistant.
- Disabled skills/tools contribute no instructions or callable tool definitions to that assistant.
- Never inject Tool Creator instructions into ordinary chats, roleplay assistants, or all system prompts by default.
- Keep permission prompts and the current isolated JavaScript execution limits.
- Do not silently change existing tool permissions.
- Do not use placeholder icons or replace the user's icon artwork.

## Progress update — 2026-11-04

- [x] Added the built-in `tool-creator` Agent Skill under `assets/builtin_skills`; it remains assistant-scoped through the existing enabled-skills mechanism.
- [x] Added assistant-specific custom-tool assignments. Existing assistants default to an empty selection; runtime tool definitions are filtered by the current assistant's selected IDs.
- [x] Added custom input-schema storage, validation, and runtime exposure. Invalid or oversized schemas are not exposed as callable tools.
- [x] Added Simple and Advanced Tool Builder modes. Simple mode accepts copy-ready instructions, input schema, implementation, and example input drafted by an assistant with Tool Creator enabled; Advanced mode exposes the technical fields. Both use the same sandbox and test-before-enable gate.
- [x] Added a direct Tool Builder shortcut from the assistant's custom-tools tab.
- [x] The adaptive launcher icon change from the preceding commit remains in place; user artwork was not redrawn or replaced.
- [x] Unit tests, debug APK assembly/upload, and Android lint all passed in GitHub Actions run [#41888159799](https://github.com/Cutie-Luna/LastNap/actions/runs/41888159799) for commit `8a44de73`. The run produced `lastnap-debug-apk`, `lastnap-unit-test-reports`, and `lastnap-lint-reports` artifacts.
- [ ] Navigation-delay profiling remains unverified. The repository has a physical-device macrobenchmark for cold startup, but no navigation-transition trace has been captured. Do not claim a performance fix until the target device is profiled.

Implementation remains on `feature/extensions-foundation`; `master` was not changed.