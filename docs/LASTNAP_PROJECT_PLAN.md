# LastNap — project plan and permanent handoff

**Plan date:** 2026-11-05  
**Primary working branch:** `lastnap-clean-foundation`  
**Protected branches:** `master`, `feature/extensions-foundation`  
**Upstream project:** `rikkahub/rikkahub`

This document is the project's durable source of truth. Read it at the start of every new chat before touching the repository. Update it at the end of every work session so that the next chat inherits the full state, decisions, reasons, file changes, test results, blockers, and next actions—not merely a list of checks.

---

## 1. What LastNap is supposed to be

LastNap is a personal version of **RikkaHub**, not a ground-up rewrite and not a redesign of its existing experience.

The intended result is:

1. The original RikkaHub app and behavior remain the foundation.
2. LastNap has its own Android application ID, displayed app name, launcher/notification artwork, and necessary LastNap-specific shortcut identifiers.
3. The agreed new feature set is added in isolated places without changing unrelated RikkaHub screens or behavior.
4. Original navigation, transitions, animations, chat behavior, settings behavior, and performance are preserved unless a specific LastNap feature truly requires a change.
5. The app remains updateable on the user's existing installation, with the same application ID and signing identity.

**Default rule:** if a file does not need to change for LastNap identity, its artwork, the requested feature, or a demonstrated build requirement, leave it byte-for-byte identical to the current upstream RikkaHub baseline.

Do not treat unrelated code as an opportunity to refactor, modernize, optimize, rename, or restyle it.

## 2. Branch safety and current repository state

### Branches

- `lastnap-clean-foundation` was created from LastNap `master` commit `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`.
- `master` is protected: do not modify, reset, force-push, or merge into it without the user's explicit permission.
- `feature/extensions-foundation` is the previous implementation branch. Keep it intact as a historical reference; do not merge it wholesale into the clean branch.
- All new implementation work belongs on `lastnap-clean-foundation`, unless the user explicitly chooses another branch.

### Full-tree baseline audit completed on 2026-11-05

The recursive Git trees were compared, not just selected Kotlin files.

| Snapshot | Files/blobs | Result |
|---|---:|---|
| New clean branch at creation | 1,536 | Same set of paths as the fork's `master` |
| Current upstream RikkaHub master at audit time | 1,536 | Same paths as the clean branch |
| Clean branch vs current upstream | — | Exactly one content difference: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt` |

The one difference exists because the upstream project received a newer context-compression input change after the fork's `master` commit. It must be reviewed and synchronized as a small, explicit upstream-baseline update during Phase 1; do not silently mix it with LastNap feature work.

### Previous implementation branch audit

At the audit point:

- `feature/extensions-foundation` pointed to `942c1b49019dbbd55f02606f31b74cc258c14641`.
- It was 122 commits ahead of the fork's `master`.
- GitHub's compare endpoint reported 66 changed paths relative to that `master`.
- The recursive tree comparison against current upstream showed 12 LastNap-only paths, 5 upstream paths absent from that branch, and 65 paths with content/presence differences.

This is a broad change set. **Do not copy the branch wholesale.** Review each change and port only what is required by the product requirements below. The old branch remains useful for recovering feature code, artwork, tests, workflow configuration, release setup, and implementation history.

### What differed on the previous branch

The full comparison identified these groups; each file must be reviewed individually before porting:

- App configuration and identity: `app/build.gradle.kts`, `AndroidManifest.xml`, shortcut resources, route handling, and release version/ABI/signing configuration.
- New custom-tool feature files: `CustomExtension.kt`, `CustomExtensionTools.kt`, `ExtensionBuilderPage.kt`, and the built-in `tool-creator` skill.
- Existing feature integration points: assistant model/settings persistence, tool factory/runtime registration, extension content/selector/pages, assistant extension settings, chat UI, and related tests.
- Locale resources: translated strings for the new builder and feature labels.
- Existing RikkaHub file edits: several settings, backup, chat, media, statistics, importer, Mermaid, notification, and provider screens. Some edits were compatibility substitutions or additive UI integrations. They are not automatically required; establish the reason for each before porting.
- Artwork/resources: LastNap cat artwork, notification artwork, adaptive launcher resources, and removal of five old density-specific launcher PNGs on the previous branch.
- Build/release infrastructure: Android verification, signed release, APK signing helper, and daily build workflows.
- Documentation and implementation notes.

The old branch also had navigation transition edits. A previous optimization replaced original slide/scale transitions with fades; that simplification was later reversed. The previous branch's final navigation transitions were restored to RikkaHub's original behavior. The clean branch already starts from the original upstream transition implementation.

## 3. Product requirements to preserve

### A. LastNap identity

- Android application ID: `com.lastnap.app`.
- Displayed app name: `LastNap`, including the supported localized app-name resources where they exist.
- Update path: retain the same application ID and the user's existing signing key.
- The user's installed LastNap 3.1.2 was version code 195. The next compatible personal release was planned as LastNap 3.1.3, version code 196. Confirm the current version values before changing them; never reduce versionCode.
- Update all identity-dependent places that genuinely need to match: Gradle application ID, manifest labels/authorities where applicable, Google Services package configuration, shortcut action identifiers, deep-link schemes, and release metadata.
- Do **not** blindly rename the Kotlin namespace/package `me.rerere.rikkahub`. Android application ID and source namespace are separate concerns. Keep the upstream namespace unless a concrete requirement proves otherwise.
- Search the entire repository for old and new application identifiers before declaring identity work complete. Classify intentional upstream namespace occurrences separately from runtime app identity.

### B. Artwork and icon behavior

- Preserve the user's supplied LastNap cat artwork exactly. Do not redraw, recolor, crop, stylize, or replace it without permission.
- Launcher icon should use Android's adaptive icon resources, with the requested pure black (`#000000`) background and the supplied foreground artwork.
- Preserve a monochrome layer if the chosen artwork supplies one and the resource is supported.
- Keep the separate notification small icon artwork (the previous implementation used `noticon`) rather than accidentally using the colorful launcher icon for notifications.
- Inspect all density-specific and adaptive resource variants together. Resource resolution must be verified; do not delete old resources based on assumption alone.
- Verify the actual packaged APK resource table and, when installation testing is appropriate, the icon shown by Android 13 on the target device.

### C. Custom tools and Tool Builder

The agreed new feature is an assistant-aware custom tool system with a built-in creator skill.

**Assistant-specific assignment**
- Custom tool definitions remain user-level records in Settings.
- Each assistant stores the IDs of custom tools selected for that assistant.
- A tool is callable only when selected for the current assistant and passes the existing enable/test/permission requirements.
- Existing assistants must migrate safely to an empty selection; do not silently expose every custom tool to every assistant.
- Deleting a custom tool must clean up its assistant references safely.
- Keep the current runtime sandbox and permission model. Do not invent browser, filesystem, network, Android, or Node capabilities for the tool sandbox.

**Simple and Advanced modes**
- Simple mode lets the user describe a tool in ordinary language and receive help preparing its name, description, input schema, implementation, and example test input.
- Advanced mode retains direct access to the technical editor, schema, code, test input/results, and permissions.
- Both modes use the exact same underlying saved tool format, sandbox, validation, test-before-enable gate, and permission rules.
- Simple mode must not bypass testing or enable a tool automatically. The user controls saving, assigning, enabling, and permissions.
- A stale test result must never enable edited code. Invalid or oversized input schemas must not become callable tools.

**Tool Creator skill**
- Add the built-in `tool-creator` Agent Skill alongside `skill-creator`.
- It is opt-in and assistant-scoped. It must not be injected globally or made available to unrelated assistants by default.
- It teaches the assistant how to create, configure, test, edit, and troubleshoot tools in the existing Tool Builder.
- The skill should give accurate guidance about the actual sandbox and never claim a tool was tested unless the current implementation's test succeeded.
- A direct Tool Builder entry point from an assistant's custom-tools area is useful, but must not change the normal assistant flow.

### D. Existing RikkaHub experience

Keep the original implementation unless a specific feature integration requires a small, documented change:

- Navigation routes and original slide/scale/fade transition specifications.
- Shared-element / hero animation behavior.
- Chat list and chat screen animation behavior.
- Settings screen structure and interactions.
- Statistics screen behavior.
- Assistant pages and existing tool behavior.
- Existing provider, backup, media, image-generation, and notification behavior.
- Existing localization and accessibility behavior.

Do not remove animations to hide lag. Do not rewrite navigation, move work across threads, change Compose state handling, or alter screen structure without a measured or reproducible reason.

### E. Shortcuts

Where custom launcher shortcuts are included, all action names, manifest intent filters, and route constants must agree. The previous LastNap convention used:

- Camera shortcut URI: `lastnap://shortcut`
- Translator action: `com.lastnap.app.action.TRANSLATE`
- Media-creation action: `com.lastnap.app.action.MEDIA_CREATION`

Verify these against the current upstream source and the actual shortcut resources before porting.

### F. Build, release, and signing

- Preserve upstream R8/release optimization and baseline-profile behavior unless a measured need justifies a change.
- The target phone is ARM64; previous release packaging was ARM64-only to avoid unnecessary ABI output.
- Keep the same private signing key for updates. The JKS and password must never enter Git, artifacts, chat messages, or this document.
- GitHub Actions may refer to repository secrets by name, but never print or store their values.
- The previous workflow used the `KEY_BASE64` and `SIGNING_CONFIG` secrets. The existing local keystore backup is held by the user; do not ask for passwords in chat.
- Signed APKs must be verified with Android `apksigner` before being described as release-ready.
- Release notes, package ID, versionName, versionCode, ABI, APK filename, checksum, and artifact must all describe the same build.
- Never publish a public release automatically. A draft release requires explicit user intent and review.

## 4. Engineering rules — apply in every chat

1. **Plan before edits.** First state the objective, scope, intended files/areas, risks, and acceptance criteria. Update this document before starting a new multi-step task.
2. **Inspect the whole repository.** Read the current branch and commit, fetch the recursive file tree, and compare it with the recorded baseline/upstream. Do not limit the audit to the files already mentioned in a chat summary.
3. **Read before writing.** Fetch the current content of every file that may change and its upstream counterpart. For binary assets, compare blob IDs, dimensions/metadata when possible, and packaged results.
4. **Minimal diffs.** Prefer a small additive file or isolated integration point. Never run broad formatter or replacement operations across unrelated files.
5. **Explain every changed file.** Record its path, what changed, why the change is required, whether it differs from upstream, and how it was checked.
6. **No speculative performance work.** Measure first. Keep performance investigation separate from feature implementation.
7. **Preserve original behavior.** Any change to an existing screen, animation, route, settings flow, data migration, or runtime registration requires a clear reason and a focused regression check.
8. **No silent scope expansion.** Do not add convenient refactors, new dependencies, visual redesigns, or unrelated compatibility changes without explaining why they are necessary.
9. **Test the changed behavior.** Use targeted tests first, then the full relevant test/lint/build pipeline. A green build does not prove device UX or performance.
10. **Be honest about status.** Distinguish source review, unit test, lint, debug build, signed release build, APK verification, installation, and on-device behavior. Never report one as proof of another.
11. **Do not make the user do repository archaeology.** Use repository tools to inspect files, logs, workflow runs, and artifacts directly whenever available. Ask the user only for device-only observations or actions that cannot be performed remotely.
12. **No merges or destructive branch operations** without the user's explicit permission.

## 5. Work plan — one focused stage per summarized chat

A chat may finish a stage early, but should not silently combine stages. If the scope changes, update the plan first.

### Stage 0 — Establish the clean foundation (current chat)

**Goal:** create a safe working branch and a durable plan before implementation.

- [x] Create `lastnap-clean-foundation` from LastNap `master`.
- [x] Leave `master` and `feature/extensions-foundation` untouched.
- [x] Compare complete repository trees against current upstream.
- [ ] Commit this project plan and session handoff to the new branch.
- [ ] Verify the new branch contains only the planned documentation change after the plan commit.

**Acceptance:** the new branch is a clean RikkaHub base plus this project document; no app source, resources, dependencies, or workflows have been changed.

### Stage 1 — Refresh and freeze the upstream baseline

**Goal:** make the clean branch match the chosen current RikkaHub upstream baseline before adding LastNap code.

- Recheck the current upstream default-branch commit; upstream can move between chats.
- Review the only known baseline difference in `CompressContextDialog.kt` and sync it if it is still the only difference.
- Compare full recursive trees again; inspect every path addition, deletion, rename, and blob mismatch.
- Record the exact upstream commit SHA and tree SHA used as the frozen base.
- Record any upstream files intentionally not synchronized and the reason.
- Confirm original navigation transitions, shared-element animation code, and baseline app startup behavior match upstream.
- Do not port feature code during this stage.

**Acceptance:** complete tree comparison is clean against the chosen upstream commit, except for documented intentional differences; baseline SHAs are written here.

### Stage 2 — Apply LastNap identity and artwork only

**Goal:** make the clean RikkaHub baseline install as LastNap without changing its behavior.

- Audit Gradle identity/version, manifest labels/authorities, app-name strings, shortcut and deep-link identifiers, Google Services configuration, notification references, and release metadata.
- Set the application ID to `com.lastnap.app`; keep the RikkaHub source namespace unless required.
- Set the app display name and all relevant localized app-name strings to LastNap.
- Recover the exact existing LastNap artwork from the previous branch or the user's supplied assets; preserve it byte-for-byte.
- Add the adaptive launcher setup with the pure black background and the correct foreground/monochrome resources.
- Keep the notification icon separate.
- Inspect all five launcher density fallbacks and adaptive resources together. Verify actual resource selection before deciding which legacy files should remain or be removed.
- Audit every occurrence of the old application ID and LastNap application ID; classify source namespace references separately.
- Change only files necessary for identity/artwork and record all of them.

**Acceptance:** LastNap installs under the correct package identity; the app name, launcher, and notification artwork are correct; original screens, routes, transitions, and behavior remain unchanged. No feature code is included yet.

### Stage 3 — Design and implement the custom-tool data model

**Goal:** introduce the smallest safe data/storage foundation for assistant-scoped custom tools.

- Inspect the existing assistant model, settings serialization, datastore migration behavior, current tool model, and runtime tool registration.
- Design the persisted custom tool record and assistant-to-tool ID relationship before editing.
- Define migration defaults explicitly; existing assistants must not gain tools automatically.
- Define validation constraints for names, descriptions, schemas, code, and size limits.
- Define deletion cleanup and behavior for missing/invalid tool IDs.
- Add focused unit tests for serialization, defaults, migration, assignment, deletion cleanup, and schema validation.
- Avoid modifying unrelated screens at this stage.

**Acceptance:** data round-trips safely; existing settings load; no custom tool becomes callable merely because it exists.

### Stage 4 — Integrate runtime, sandbox, and safety gates

**Goal:** make selected tools callable only by the correct assistant and only after explicit user approval.

- Trace the complete current tool-registration path from assistant selection to model request.
- Filter custom tool definitions by the current assistant's selected IDs.
- Preserve the current isolated JavaScript sandbox and its real capability limits.
- Preserve test-before-enable and explicit permission approval.
- Reject invalid/oversized schemas and untested or edited-after-test implementations.
- Ensure tool deletion removes stale assignments.
- Add runtime tests for included/excluded assistants, disabled tools, permission handling, failures, and changed-code test invalidation.
- Do not change built-in RikkaHub tools or provider behavior.

**Acceptance:** each assistant receives exactly its selected, valid, enabled, permission-approved custom tools; all other assistants receive none.

### Stage 5 — Add the Simple and Advanced Tool Builder UI

**Goal:** add the two entry modes without replacing or damaging the existing editor.

- Review the existing Extension Builder implementation from the previous branch before porting it.
- Keep Advanced mode faithful to the existing technical editor and its behavior.
- Add Simple mode as a guided natural-language route to the same fields and saved tool format.
- Both modes must share validation, sandbox, permission, and test-before-enable logic.
- Keep saving, enabling, assigning, and granting permissions under explicit user control.
- Add targeted UI/state tests where the current architecture supports them.
- Verify empty, invalid, oversized, loading, failed-test, stale-test, and successful-test states.

**Acceptance:** Simple and Advanced modes produce the same underlying tool record and neither mode bypasses safety gates.

### Stage 6 — Add the Tool Creator skill and assistant entry points

**Goal:** integrate the built-in skill into the existing skill and assistant systems.

- Review the existing Agent Skill loading/enablement rules.
- Add `tool-creator` alongside `skill-creator`.
- Keep it disabled until explicitly enabled for the relevant assistant.
- Ensure its instructions are not injected globally.
- Add or restore the assistant-specific custom-tool selection UI.
- Add a direct Tool Builder entry point only within the relevant assistant's custom-tools area.
- Verify assistants without the skill/tools do not receive their instructions or callable definitions.
- Ensure roleplay/general assistants remain unaffected unless the user assigns the feature.

**Acceptance:** the feature is opt-in and assistant-scoped end to end; unrelated assistant experiences remain unchanged.

### Stage 7 — Locales, shortcuts, and integration polish

**Goal:** finish only the user-visible resource and integration work required by the implemented feature.

- Port only the required new strings to the supported locale files.
- Check every resource key exists in the base locale and supported translations; do not rewrite unrelated translations.
- Verify launcher shortcut actions, manifest filters, and route constants together.
- Verify the app label, launcher icon, round icon, monochrome icon, notification small icon, and app links.
- Confirm settings backup/restore behavior includes the new persisted fields if applicable.
- Record each resource or existing-screen file touched and its exact reason.

**Acceptance:** feature labels are complete, shortcuts resolve, backup/restore remains safe, and no unrelated UI has been altered.

### Stage 8 — Full verification and release readiness

**Goal:** prove the feature and the LastNap package can be built and updated safely.

- Run targeted unit tests for every changed subsystem.
- Run the full unit test suite.
- Run Android lint.
- Assemble the debug APK.
- Assemble the optimized signed ARM64 release APK.
- Verify APK package ID, versionName, versionCode, ABI, icon resources, and signing certificate.
- Verify APK signature with `apksigner`.
- Upload test reports, lint reports, APK, mapping file, checksum, and build metadata as appropriate.
- Inspect CI logs and artifacts directly; record workflow run IDs and exact commit SHAs.
- Do not call a release ready until the signed APK is available and verified.
- Do not publish the release; prepare a draft only when the user explicitly asks and review it with them.
- On-device installation/update testing is a separate acceptance item and must be labeled as such.

**Acceptance:** all required CI tasks pass; APK identity/signature/version are consistent; user can review the artifact before installation.

### Stage 9 — Performance investigation (separate from feature work)

**Goal:** understand the reported lag without sacrificing RikkaHub behavior.

Known user observations from the previous build:
- The first opening/use of some animated UI stutters; subsequent use becomes smoother.
- Navigation to some screens, including Settings from the chat drawer and Statistics, feels delayed (roughly two seconds was reported).
- RikkaHub itself appeared smoother on the same itel P55.
- Original animations were restored on the previous branch; do not remove them again as a speculative fix.

Known evidence and limitations:
- `HeroAnimation.kt` and the original shared-element animation implementation matched upstream in the prior parity review.
- `StatsVM` and `SettingPage` matched upstream at that review point.
- `ChatPage` and `StatsPage` had small API/locale compatibility differences.
- Baseline Profile source files were empty in both the previous LastNap branch and upstream at the time of review; their emptiness is not evidence of a LastNap-only regression.
- No Perfetto or Macrobenchmark trace has yet identified the cause on the target phone.
- No measured performance fix has been established.

Procedure:
1. Build comparable optimized release variants from the selected upstream baseline and LastNap branch with matching settings.
2. Record cold startup, first navigation, warm navigation, Settings entry, Statistics entry, and first-use animation behavior on the same target device.
3. Capture Perfetto/system trace and Compose/recomposition evidence where available.
4. Compare main-thread work, frame timing, startup initialization, navigation composition, database work, and blocking I/O.
5. Identify one measured bottleneck and propose the smallest targeted change.
6. Capture the same trace again after the change and compare results.
7. Keep animations and effects intact unless evidence shows a specific one is the source and the user approves the behavior change.

**Acceptance:** a documented before/after measurement on the target device. If no device trace can be collected, report the blocker and do not claim the lag is fixed.

## 6. Previous release and testing history

This history is useful context, not a guarantee that the current clean branch has been tested.

- A LastNap 3.1.2 ARM64 APK was previously built and signed with application ID `com.lastnap.app`, version code 195. Signature verification passed. Its recorded SHA-256 was `fce2bce0b67a565ca8dafb98ea16851f0d092ced10f7a67188b67af200e72d27`.
- The previous personal signing keystore is backed up locally by the user. Preserve it for update compatibility; never copy it into the repository.
- The previous release workflow passed in run 41897065669; the corresponding Android verification run 41897065076 passed at that time.
- Unit tests, debug APK assembly/upload, and lint previously passed in run 41888159799.
- On 2026-11-05, verification run 41989830645 completed successfully for previous-branch commit `942c1b49019dbbd55f02606f31b74cc258c14641`.
- At the same check, release workflow run 41989830683 for that commit was still in progress. Recheck its final status before relying on it.
- Earlier release workflows triggered by intermediate launcher-icon commits were cancelled when newer commits arrived; do not count those cancelled runs as release passes.
- The clean branch has not yet had its own build, test, lint, signed APK, or device installation verification.

## 7. File-change ledger

Update this table at the end of every chat. Add rows rather than erasing previous history.

| Date / chat | Branch and commit | Files added/changed/deleted | Reason and behavior impact | Verification / outcome |
|---|---|---|---|---|
| 2026-11-05 — clean foundation created | `lastnap-clean-foundation`, based on `903e058b9361bff71d0b79f4ef2d8ac23d6597e6` | Pending this document commit | New clean starting point; app source/resources not changed | Full-tree audit: 1,536 paths; one upstream content difference documented above |
| 2026-11-04 to 2026-11-05 — previous implementation | `feature/extensions-foundation`, audit head `942c1b49019dbbd55f02606f31b74cc258c14641` | 66 changed paths relative to old fork master; detailed GitHub compare remains available | Prior identity, custom-tool work, UI integration, assets, workflow, locale and compatibility changes; must be selectively reviewed before porting | Earlier CI runs passed as listed; latest release run still needed recheck; no measured device performance result |

### Per-chat handoff template

Every new chat must append a filled handoff using this structure:

- **Date and objective:**
- **Branch and starting commit:**
- **Plan stage:**
- **Repository audit:** file-tree counts, upstream SHA, added/deleted/renamed/modified paths, and any unexplained mismatch.
- **Files changed:** for every file, state what changed and why it was necessary. Include binary assets and workflows.
- **Decisions:** what was chosen, what was rejected, and the reason.
- **Tests/checks:** exact command or workflow, run ID, commit SHA, and pass/fail/not-run status.
- **Artifacts:** exact artifact names, APK metadata/checksum/signature when applicable.
- **Device observations:** what was actually observed and by whom; do not infer.
- **Known issues and blockers:**
- **Next chat:** one focused objective, intended files/areas, risks, and acceptance criteria.

At the start of the next chat:
1. Read this whole document.
2. Query the current branch, HEAD, recent commits, and workflow status.
3. Fetch the complete recursive repository tree and compare against the recorded baseline and live upstream.
4. Review all changed paths and relevant file contents before editing.
5. State the stage plan, then proceed only within that scope.

At the end of the chat, update this document before presenting the handoff summary. Never leave important decisions, reasons, or failed attempts only in conversation history.

## 8. Current next action

Finish Stage 0 by committing this document to `lastnap-clean-foundation`, then verify that the branch differs from its starting commit only by this documentation file. Do not begin app code changes in the same stage.
