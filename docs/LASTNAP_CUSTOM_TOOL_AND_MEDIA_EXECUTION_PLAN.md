# LastNap — Custom Tools and Inline Media Execution Plan

**Created:** 2026-11-06  
**Working branch:** `feature/custom-tool-api-runtime`  
**Protected baseline:** `lastnap-clean-foundation` at `8e4ac854df5d15d35a9f7853698ebf4dfe8e0653`

> This is a new, standalone execution plan. It does not replace, edit, renumber, or mark complete any stage in `docs/LASTNAP_PROJECT_PLAN.md`. The existing plan and its historical stages remain untouched. All implementation work described here is confined to the working branch above.

## Mission

Complete the user's intended custom-tool experience, then add inline Markdown video playback. Custom tools must be able to call real user-configured HTTP APIs through a narrow native host bridge, use named Secrets and Variables, optionally persist data in an isolated folder owned by each tool, and return media that the chat/model can actually consume. The existing Tool Builder remains the place where the user configures, tests, enables, and assigns tools.

After the custom-tool capability is complete and regression-tested, implement the agreed borderless, inline, YouTube-like Markdown video player.

## Non-negotiable boundaries

- Never modify `lastnap-clean-foundation`, `master`, or `feature/extensions-foundation`.
- Work only on `feature/custom-tool-api-runtime`.
- Do not modify the old project plan or its stages.
- Do not redesign or refactor unrelated app features, navigation, settings, shared styling, performance, or integrations.
- Preserve existing RikkaHub behavior, SSH SecretCipher behavior, Custom JS search behavior, existing custom-tool approval flow, and existing assistant-scoped assignment.
- No broad dependency upgrades. Add a dependency only when the selected implementation demonstrably requires it.
- No secrets or secret ciphertext in tool definitions, Settings exports, database backups, tool exports, prompts, JavaScript, test fixtures, logs, diagnostics, or tool results.
- QuickJS receives no direct Android, filesystem, Node, browser, process, or unrestricted network access. All capabilities are explicit, opt-in, and mediated by narrow native interfaces.
- The single existing per-tool **Need approval** switch remains the only approval control. Do not create another approval system.
- Storage is per tool, not per assistant or App Manager. It is shared across assistants that use the same tool and created lazily on first successful write.
- Do not begin video-player implementation until the custom-tool completion gate passes.
- Never claim a test, build, APK verification, installation, or device behavior that has not actually been observed.

## Stage 0 — Establish an isolated execution workspace

**Goal:** verify the exact branch/base and make the new plan the only initial change.

Tasks:
- Verify current branch head and compare it with the recorded base.
- Confirm the existing project plan is unchanged.
- Confirm the protected branches remain untouched.
- Create this file as a separate plan document.
- Record the initial commit and changed-path list.

Acceptance:
- Only this new plan file is added in this stage.
- No source, resource, workflow, dependency, or old-plan change is made.

## Stage 1 — Complete the architecture audit and freeze contracts

**Goal:** remove design ambiguity before source edits.

Inspect current source and direct call sites for:
- Custom tool model, persistence, validation, test fingerprint, Builder state/page, runtime dispatch, QuickJS lifecycle, DI registration, and built-in Tool Creator skill.
- Existing QuickJSFetch and its Custom JS search caller, OkHttp client ownership, cancellation, redirects, and coroutine lifecycle.
- SecretCipher, Android Keystore behavior, backup and restore paths, Settings serialization, manual backup, and tool import/export.
- FilesManager, FilesRepository, managed-file records, file URI handling, deletion, and app-private storage.
- UIMessagePart media variants, provider tool-result serialization, vision modality checks, chat rendering, and Markdown image rendering.
- Existing Media3/ExoPlayer dependencies, player components, Markdown parser, local thumbnail support, and lifecycle handling. This is audit only; no video implementation yet.

Freeze and document these contracts:
1. Persisted opt-in capability flags with safe defaults for existing tools.
2. A single documented named-reference convention shared by Simple and Advanced modes.
3. Tool-declared Secrets and Variables, with required/optional status and descriptions.
4. Dedicated CRUD experience for named values. Secret values are masked and never read back into Builder code or AI context.
5. Native request-time secret resolution. Do not provide a generic plaintext secret getter to JavaScript. Define supported injection locations and reject unsafe/ambiguous substitutions.
6. Missing required values are clearly identified before a tool can be enabled or invoked.
7. HTTP method, headers, query, JSON/text/binary request bodies, response status/headers, JSON/text/binary response access, timeouts, cancellation, redirect policy, URL policy, response-size limits, and error shape.
8. Explicit opt-in enforcement at runtime, not just in the Builder UI.
9. Per-tool storage API, safe relative names, quotas, size limits, lazy directory creation, and cross-tool isolation.
10. Retention and deletion behavior for tool removal, storage disablement, tool re-import, and app backup/restore.
11. Test fingerprint inputs: all executable code/schema/capability declarations that affect execution, but never secret values.
12. Regression matrix proving Custom JS search and SSH credential flows remain unchanged.

### Media ownership and lifecycle contract

This contract is separate from tool-owned storage and is mandatory for every supported media type (initially images; later any audio/video/document type only when fully supported):

- **Conversation media belongs to the conversation, never to the producing tool.** Once a generated result has been committed to a chat message, deleting, disabling, editing, or re-importing the originating tool must not invalidate that result.
- Store generated chat media through a conversation-owned managed-media path/record, not inside the tool's private storage directory. Tool storage is temporary/private working data; chat media is durable user-visible output.
- Associate each media reference with its owning conversation and message. Deleting one conversation removes its messages and all media references/assets owned only by that conversation. It must not affect any other conversation, even if another message references the same underlying bytes.
- If physical blobs can be shared/deduplicated, use explicit ownership/reference records and delete a blob only after its final live reference is removed. Otherwise use independently owned copies. Never infer ownership from filename, tool ID, URL, or matching content.
- Message deletion, regeneration, editing, truncation, and conversation deletion must clean up only media no longer referenced by any surviving message. Cleanup must be idempotent and safe after crashes.
- Tool deletion must never cascade into conversation messages, media references, or conversation-owned media files.
- Treat media publication as an atomic boundary: a result is either committed to its target message with durable media ownership, or remains temporary and is cleaned up. No half-published message, dangling reference, or permanent orphan is acceptable.
- If a tool is deleted while executing, block new invocations and cancel/invalidate in-flight work. Any output already atomically committed to a conversation remains; uncommitted temporary output is discarded. A late callback must not resurrect a deleted tool or publish an unowned file.
- Chat backup/restore must preserve the relationship between messages and their media, or explicitly report missing media and render a safe placeholder. Tool deletion must not change chat backup contents. Tool exports must not contain conversation media.
- When a conversation is deleted, remove its associated media from managed storage and metadata without touching media belonging to other conversations. If the same underlying blob has references in multiple conversations, remove only the deleted conversation's references and retain the blob until no references remain.
- Failed cleanup must be recoverable: mark pending deletion, retry safely, and never expose deleted media in another chat. Avoid deleting files before the metadata transaction establishes that no live owner remains.

Acceptance:
- Tests prove tool deletion leaves old chat media readable.
- Tests prove deleting chat A removes its unshared media but leaves chat B and its media readable, including a shared-blob/reference-count scenario if deduplication exists.
- Tests cover message-only deletion/regeneration, repeated deletion, process death between metadata/file operations, tool deletion during an active media-producing call, and backup/restore consistency.

Acceptance:
- Contracts and risks are documented in this new plan before source edits.
- Any design that would require unrelated app changes is narrowed or rejected.

## Stage 2 — Implement the opted-in HTTP/API runtime

**Goal:** make custom tools capable of real HTTP API calls without granting network access to other scripts.

Tasks:
- Reuse and extend the existing native QuickJS fetch bridge behind a custom-tool-only adapter.
- Preserve Custom JS search's current contract and behavior; add focused regression tests around its caller.
- Add only the request/response features required by the frozen contract, including binary-safe transfer for media.
- Enforce opt-in capability at the host boundary.
- Enforce timeout, cancellation, request/response size limits, safe URL handling, redirect policy, and clear errors.
- Ensure HTTP response metadata and binary data are not silently converted into lossy text.
- Add fake-server tests for supported methods, query/headers/bodies, response forms, errors, timeouts, cancellation, limits, redirects, and disabled capability.

Acceptance:
- Opted-in tools can call a local fake HTTP server and receive correct structured results.
- Non-opted-in tools cannot access the bridge.
- Existing Custom JS search tests pass unchanged or with strictly additive regression coverage.
- No unrestricted host APIs are exposed to QuickJS.

## Stage 3 — Implement named Secrets and Variables

**Goal:** provide GitHub Actions / Cloudflare-style named configuration without leaking sensitive values.

Tasks:
- Add a dedicated local secret store, separate from Settings and database exports, using Android Keystore-backed encryption and app-private no-backup storage.
- Preserve the existing SecretCipher alias, SSH model, and SSH call sites.
- Store ordinary Variables separately from sensitive Secrets and define their backup/export behavior explicitly.
- Add per-tool declarations of required and optional names, descriptions, and intended use.
- Add a dedicated masked management flow to create, update, and delete named values.
- Resolve secret references only inside native request construction. JavaScript, AI prompts, test output, diagnostics, logs, tool exports, and serialized definitions never receive the plaintext.
- Make missing required values visible and actionable.
- Handle reinstall/device restore by asking the user to recreate unavailable local Secrets; never silently substitute or export them.
- Add tests for encryption/decryption, persistence, atomic writes, missing values, redaction, backup/export exclusion, and unchanged SSH behavior.

Acceptance:
- The user enters a Secret once under its declared name.
- Tool code references only the name.
- Secret plaintext never crosses into JS or model-visible content.
- Variables remain ordinary non-sensitive configuration.
- Existing SSH credentials continue to work.

## Stage 4 — Implement isolated optional per-tool storage

**Goal:** let a tool persist its own files/data without general filesystem access.

Tasks:
- Add a narrow native storage service that derives its private directory from an immutable tool ID.
- Expose only safe relative filenames and bounded read/write/list/delete operations that the product contract actually needs.
- Create the folder only on the first successful write when storage is enabled.
- Keep one folder per tool, shared across assistants using that tool.
- Enforce per-tool and global quotas, file-size limits, path traversal protection, and safe file types.
- Define storage disablement, tool deletion, import/re-import, and retention semantics. Disabling storage must not silently erase existing data.
- Tool-owned storage is not conversation media. Deleting a tool removes its own private storage and its own configuration/credentials according to the deletion contract, but never deletes files already published into chats.
- Define crash-safe tool deletion as a staged/idempotent operation: mark tool unavailable and prevent new runs; cancel active runs; remove assistant assignments and tool-owned configuration/secret/variable records; clean only the tool's private storage; then finalize deletion. Recover incomplete cleanup on next launch. Preserve any already-committed conversation results.
- Keep immutable tool identity distinct from display name and import identity. Re-importing a tool must not silently regain access to deleted tool data or another tool's namespace. Explicit restore/import semantics must be defined.
- Storage disablement blocks future reads/writes but retains existing data until the user explicitly deletes it or deletes the tool; show that retained data exists and explain its ownership.
- Define behavior for tool deletion while a call is running and for storage writes racing with deletion. A late completion must not recreate deleted storage.
- Keep tool storage separate from the Secret store.
- Exclude tool data from backup unless an explicit product decision and safe implementation are documented.
- Add tests for lazy creation, restart persistence, tool isolation, shared assistant access, quotas, path traversal, disablement, deletion, and import behavior.

Acceptance:
- A tool cannot access another tool's data or arbitrary app files.
- Its data survives app restart.
- No folder exists before the first successful write.
- Existing App Manager and managed-file behavior is unchanged.

## Stage 5 — Finish the Tool Builder and Tool Creator experience

**Goal:** make all implemented capabilities understandable and configurable in the existing Builder.

Tasks:
- Extend the existing full-page Builder; do not rebuild it.
- Add clear API access opt-in and concise explanation.
- Add named Secret/Variable declarations with required/optional status and descriptions.
- Add optional per-tool storage controls and clear retention explanation.
- Show missing configuration and capability requirements before testing/enabling.
- Include capability declarations in stale-test invalidation/fingerprinting.
- Update Simple and Advanced AI guidance to use the same reference syntax and explain setup names without asking for values.
- Keep AI assistance for every field in both modes.
- Update Review to summarize API access, required names, storage, approval, and test status.
- Update the built-in Tool Creator skill only to describe capabilities that are implemented and tested.
- Preserve the existing real QuickJS test, test-before-enable gate, stale-test invalidation, explicit save/enable/assignment, assistant-scoped selection, and one **Need approval** switch.
- Add focused model, validation, Builder-state, UI-state, and skill tests.

Acceptance:
- Simple and Advanced modes describe the same real capabilities.
- User remains in control of placing AI-generated content, testing, saving, enabling, assigning, and approval.
- The Builder and skill do not advertise unimplemented capabilities.
- All existing custom-tool behavior and approval flow remain intact.

## Stage 6 — Managed media results and end-to-end model handoff

**Goal:** let API-backed tools return useful media, not huge text blobs.

Tasks:
- Add typed image result handling for API binary/Base64 responses: decode natively, validate format/size, save through managed storage, and return a typed image UIMessagePart.
- Preserve provider-specific vision behavior and verify the selected model's image modality before promising image understanding.
- Ensure the image is rendered in the tool result and can be passed to the model using the existing provider adapters.
- Verify managed local image paths in Markdown without breaking existing image/GIF rendering.
- Investigate audio/video/document parts only where both provider input serialization and UI rendering are genuinely supported. Do not advertise unsupported media types.
- Keep Base64 out of model context when typed media is supported.
- Add end-to-end tests for binary response -> managed file -> typed part -> renderer/provider handoff, plus invalid/oversized data.
- Persist chat-generated media under conversation/message ownership, never in tool-owned storage. The producing tool ID may be retained as non-owning provenance metadata, but must not control media lifetime.
- Ensure the message/media reference is committed atomically enough that the UI cannot show a durable message whose media was never safely stored. Clean temporary files on cancellation/failure.
- Verify media remains available after tool disable, tool deletion, tool edit, and tool re-import.
- Verify deleting one conversation removes only its media and references; other conversations remain intact. If deduplicated blobs are used, validate reference counting/ownership and final-owner garbage collection.
- Verify deleting a message, regenerating a response, or clearing a conversation releases only media no longer referenced by surviving messages.
- Test a tool deletion racing an active API/media call: committed results survive; uncommitted results are cancelled and cleaned; no late callback recreates tool-owned state or leaks orphaned files.

Acceptance:
- A tool can return a real image through the supported native path.
- The image appears in chat and is available to vision-capable model adapters.
- Existing image/GIF, ordinary Markdown, and provider behavior remain unchanged.

## Stage 7 — Custom-tool completion and regression gate

**Goal:** prove the complete custom-tool feature before beginning video work.

Tasks:
- Run all focused custom-tool, QuickJS, credential, storage, media ownership/lifecycle, settings normalization, backup/restore, and skill tests.
- Verify chat backup/restore preserves media ownership and message references, and that deleting a tool does not alter chat backup data.
- Run lint, all unit tests, debug APK build, and the configured signed ARM64 release workflow.
- Inspect workflow logs and artifacts; verify APK package, ABI, signature, and version metadata where the workflow provides them.
- Review the full diff against the recorded base. Reject unrelated file changes.
- Confirm no secrets or ciphertext entered the repository or artifacts.
- Record exactly which checks passed and which remain unverified.

Acceptance:
- All relevant automated checks pass.
- API/network and storage are opt-in and enforced by runtime tests.
- Credentials are isolated and redacted.
- SSH and Custom JS search regression tests pass.
- The custom-tool implementation is stable enough to proceed to the independent video stage.
- No claim of device verification is made until the user actually tests it.

## Stage 8 — Implement inline Markdown video playback

**Start only after Stage 7 passes.**

**Goal:** render video exactly where it appears in the AI's Markdown, not as a card or separate attachment area.

Behavior:
- Inline, borderless, no extra card/frame/padding, natural aspect ratio, bounded by chat width.
- No autoplay.
- Local video shows its actual first frame without starting playback.
- Remote video URL is not fetched/downloaded merely to render or create a preview. Use a supplied poster if available; otherwise show a lightweight placeholder until explicit Play.
- Tap Play to load/start. While controls are visible, tap empty video area to hide controls without pausing. Tap again to reveal them.
- Controls fade after inactivity during playback; controls remain visible while paused.
- Overlay play/pause, seek/progress, elapsed/total time, ±10-second skip, and fullscreen.
- Handle buffering, load/play errors, lifecycle/backgrounding, orientation/fullscreen restoration, and accessibility.
- Preserve existing image/GIF/link Markdown behavior.
- Prefer the existing native media stack if present; select Media3/ExoPlayer only after dependency and lifecycle audit. Avoid WebView unless a concrete compatibility requirement justifies it.
- Offline caching is optional and must never consume storage silently.

Tests:
- Markdown placement and mixed text/video ordering.
- No autoplay.
- Local first-frame preview without playback.
- Remote no-fetch-before-Play behavior and supplied poster/placeholder.
- Tap-to-show/hide controls, auto-fade, paused persistence, seek, skip, fullscreen.
- Buffering, errors, lifecycle/backgrounding, accessibility.
- Image/GIF/links unchanged.

Acceptance:
- Video behaves as part of the Markdown message, not a card or separate attachment.
- Remote sources remain unloaded until explicit Play.
- Existing Markdown behavior remains intact.

## Stage 9 — Final integration, release verification, and handoff

Tasks:
- Run the full relevant test/lint/debug/release pipeline again after video integration.
- Re-run media ownership/deletion/backup tests for every newly supported media type; do not assume image coverage proves video/audio/document lifecycle correctness.
- Review all changed paths against the base and verify that only this plan's explicitly scoped custom-tool/media files changed.
- Inspect release artifact metadata/signature if produced.
- Update this new plan with commit SHAs, changed-file explanations, test/workflow links, and known limitations.
- Keep the old project plan untouched.
- Do not merge into protected branches or publish a release without explicit user permission.

Acceptance:
- Automated verification is complete and accurately recorded.
- Remaining manual/device checks are clearly separated from CI checks.
- Working branch is ready for user review; no protected branch was changed.

## Execution log

### Initial state (2026-11-06)
- Existing `docs/LASTNAP_PROJECT_PLAN.md` was read from `feature/custom-tool-api-runtime`; this new plan is intentionally separate.
- The existing plan describes the current feature branch as documentation-only, with no custom-tool runtime source implementation yet.
- The recorded base is `8e4ac854df5d15d35a9f7853698ebf4dfe8e0653`.
- The prior architecture notes identify an existing QuickJSFetch bridge, SecretCipher, FilesManager primitives, and typed UIMessagePart media as possible foundations, not completed custom-tool capabilities.
- No source code has been changed as part of this new plan.
- Stage 0 plan-file creation is the first intended repository mutation; later stages must be completed and logged individually.
