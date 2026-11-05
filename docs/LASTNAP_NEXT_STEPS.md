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
- [x] Restored the LastNap cat artwork as both launcher and round icon (`@drawable/appicon`); kept the existing white `@drawable/noticon` for notification small icons.
- [x] Unit tests, debug APK assembly/upload, and Android lint all passed in GitHub Actions run [#41888159799](https://github.com/Cutie-Luna/LastNap/actions/runs/41888159799) for commit `8a44de73`. The run produced `lastnap-debug-apk`, `lastnap-unit-test-reports`, and `lastnap-lint-reports` artifacts.
- [x] An earlier performance pass replaced the original full-screen slide/scale navigation transitions with short fades; the 2026-11-05 source-parity review reversed that simplification and restored the original RikkaHub slide/scale transitions.
- [ ] The target itel P55 has not been profiled with Android Macrobenchmark/Perfetto. Navigation transitions are lighter now, but cold-start and page-entry latency still need measurement on the actual device; don't claim a fully measured performance fix.

Implementation remains on `feature/extensions-foundation`; `master` was not changed.
## Follow-up release and performance work — 2026-11-04

- [x] Compared the app with upstream RikkaHub 3.1.1 (`https://github.com/rikkahub/rikkahub/releases/tag/3.1.1`). Upstream ARM64 APK: 16,250,504 bytes. LastNap 3.1.2 signed ARM64 APK: 16,126,412 bytes (15.38 MiB), about 124 KB smaller.
- [x] Confirmed R8 release optimization, ARM64-only native libraries, and committed Baseline Profile assets are present in the release APK (`assets/dexopt/baseline.prof` and `baseline.profm`).
- [x] Verified the packaged resource table contains both `appicon` and `noticon`; the manifest now points to the LastNap artwork, while notification construction continues using `R.drawable.noticon`.
- [x] Built and signed `LastNap-3.1.2-arm64-v8a.apk`, application ID `com.lastnap.app`, version code 195, using Android APK Signature Schemes v2 and v3. `apksigner verify` passed. APK SHA-256: `fce2bce0b67a565ca8dafb98ea16851f0d092ced10f7a67188b67af200e72d27`.
- [x] Release CI run [#41897065669](https://github.com/Cutie-Luna/LastNap/actions/runs/41897065669) passed release assembly, ARM64 packaging, and R8 mapping upload. The corresponding verification run [#41897065076](https://github.com/Cutie-Luna/LastNap/actions/runs/41897065076) passed unit tests, debug APK assembly, and lint.
- [x] Exported Android `apksigner`/`zipalign` in helper workflow run [#41897646099](https://github.com/Cutie-Luna/LastNap/actions/runs/41897646099) so the personal release could be signed locally without committing a private key.
- [x] Created a new personal signing keystore locally (not in the repository). Certificate SHA-256: `633bcb19f5dab86c5bf253b107458b4a9eb8bc332538d8fcdf28cbd644007787`. Keep the JKS and its separate password file safe; every future release update must use this same key. If lost, a differently signed APK cannot update this install in-place.
- [ ] Have the user test 3.1.2 on the itel P55 and report cold-open time, first screen-ready time, and which page transitions still stutter. Then use a release Macrobenchmark/Perfetto trace on a real device before changing startup initialization or Compose screens.
- [ ] Consider regenerating the Baseline Profile on a representative lower-end device for cold start, conversation opening, history, settings, assistant detail, and extension builder. The committed upstream profile exists and is packaged, but it has not yet been re-recorded for LastNap's changed startup/navigation paths.

## Future small-update workflow

- Prefer isolated small changes over broad refactors. For performance-related changes, measure before and after on the target device.
- Keep the same application ID (`com.lastnap.app`) and monotonically increase versionCode for each release.
- Sign every future personal update with the same private `LastNap-3.1.2-signing.jks` (alias `lastnap-release`). Do not commit the keystore or password to GitHub. Retain a separate backup of both.
- Keep releases ARM64-only for this device; preserve R8, locale filtering, unused-class exclusions, and Baseline Profile unless measurements justify a change.

Implementation remains on `feature/extensions-foundation`; `master` was not changed.

## Full upstream parity audit — 2026-11-05

- [x] Compared the complete source/resource tree against upstream RikkaHub. No upstream files are missing from the LastNap feature branch.
- [x] Reviewed all 34 modified Kotlin source files and their removed diff lines. Apart from the previously simplified navigation transitions (now restored), the changes are additive LastNap functionality or compatibility substitutions for Compose context/resource APIs; no other animation/effect removal was identified.
- [x] Confirmed the shared-element animation implementation and the chat-list animated-content calls remain in place and match upstream.
- [x] Fixed launcher shortcut targets so the camera shortcut uses `lastnap://shortcut`, and translator/media-creation shortcuts use the matching `com.lastnap.app.action.*` actions registered by the manifest.
- [x] Synchronized the newest upstream context-compression control, including direct numeric entry for the number of recent messages to keep.
- [x] Bumped the next personal update to LastNap 3.1.3, version code 196, so it can update an installed 3.1.2/version-code-195 build with the same application ID and signing key.
- [ ] Verify final unit tests, lint, signed release APK assembly, APK signature, and install/update behaviour on the target itel P55.
