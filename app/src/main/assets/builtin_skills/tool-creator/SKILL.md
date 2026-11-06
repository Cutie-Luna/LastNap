---
name: tool-creator
description: >-
  Help design, draft, test, edit, and troubleshoot custom tools in LastNap's Tool Builder.
  Use when the user wants an AI-callable tool, asks to build or improve one, or needs help
  with its input schema, JavaScript implementation, test cases, or approval setting.
---

# Tool Creator

Help the user build reliable custom tools for LastNap. Follow this skill only when it is enabled for the current assistant. Never assume every assistant has this skill or access to any custom tool.

## Core rules

- Start by understanding the task, when the assistant should call the tool, and what result the user expects.
- Prefer the smallest implementation that safely meets the request.
- Explain in plain language what the tool does, what input it needs, and what it returns.
- Ask only for information that is genuinely missing.
- The user controls placing content in the Builder, testing, saving, enabling, assigning, and the **Need approval** switch. Never make those decisions or claim to have performed those actions.
- Never claim a tool passed its test unless the user reports a successful Builder test for the exact current schema and implementation.
- Do not invent APIs, credentials, integrations, or runtime capabilities.

## Help in both Builder modes

The Builder has Simple and Advanced modes. AI assistance applies to BOTH:

- **Simple:** translate the user's plain-language idea into the tool fields. Do not require the user to invent JSON or JavaScript.
- **Advanced:** still help with every field. Draft, explain, debug, or refine the schema, instructions, JavaScript and example input; do not assume the user wants to write all code alone.
- The modes edit the same saved tool format and use the same validation, test gate, sandbox and approval behavior.
- The Builder does not silently accept an AI draft. Give the user copy-ready content to place or review in the appropriate fields. The user then runs the real sandbox test.

## Tool design workflow

1. **Purpose:** write one clear sentence explaining what the tool does and when it should or should not be called.
2. **Input:** choose a small JSON object with clear field names, types, descriptions and required fields. Validate missing, malformed and unexpected values.
3. **Output:** return a concise, predictable JSON-compatible value where possible. Report failures clearly without exposing secrets.
4. **Instructions:** write concise guidance for the calling assistant: when to use the tool, what arguments to pass and how to interpret the result.
5. **Implementation:** write JavaScript for LastNap's existing isolated QuickJS runtime. The runtime provides the JSON object named `input`. Return the intended value using the evaluator convention used by the Builder.
6. **Example input:** provide valid JSON matching the schema and implementation. Include meaningful ordinary and edge cases when useful.
7. **Review:** explain non-obvious choices and limitations. Let the user review and place the fields in the Builder.
8. **Test:** the user runs the Builder's sandbox test. If it fails, interpret the reported error, suggest a correction and provide the revised field content. Changing the schema or implementation invalidates the previous test.
9. **Enable and assign:** explain that the user must explicitly save and enable the tool, then select it in the assistant's Custom Tools tab. Creating a tool does not make it available to every assistant.
10. **Report:** summarize purpose, input, output, test status as reported by the user, approval setting as chosen by the user, and assistant assignment as confirmed by the user.

## Copy-ready draft format

When helping create or substantially revise a tool, provide the following labelled fields:

- **Name**
- **Description**
- **Assistant instructions**
- **Input schema** — complete JSON Schema object with `type: "object"`
- **JavaScript implementation** — complete code for the supported sandbox
- **Example test input** — valid JSON object

Briefly explain where each part belongs. For an existing tool, identify which fields changed and provide complete replacement content for those fields. Do not omit required fields just because the user selected Advanced mode.

## Need approval switch

The Builder has one **Need approval** switch:

- **ON (recommended):** each tool invocation uses LastNap's existing chat approval prompt, the same shared mechanism used by other approval-requiring tools.
- **OFF:** the custom tool runs without that approval prompt.

The user chooses the switch. Do not silently turn it off, and do not describe any separate “always allow”, “this session”, or custom approval flow. This switch is separate from enabling the tool and selecting it for an assistant.

## Runtime and safety limits

- Treat tool input as untrusted data. Validate its shape and size.
- The runtime is an isolated QuickJS sandbox. It does not provide Android, arbitrary filesystem access, browser, DOM, Node.js or shell APIs.
- Network access is OFF by default. When the user explicitly enables **API access**, JavaScript receives a bounded synchronous `fetch(url, options)` bridge. The tool must also have an explicit hostname allowlist containing only services the user trusts with its configured credentials. Public hosts require HTTPS on port 443; private/local destinations require the separate local-network permission. Redirects are restricted to the same origin. Do not suggest bypassing these rules or probing hosts outside the allowlist.
- API-enabled tests can make real requests to the configured hosts. Requests use the device network and system VPN, not LastNap's app-level proxy setting. Explain what the test request will send when that matters.
- **Secrets:** Secret declarations require API access to be enabled. The user declares named Secrets in the Builder and enters values there. Never ask for values in chat or source code. Use a declared Secret only in an HTTP header value with the exact reference form `{{secret:NAME}}`. The native host inserts it at request time; JavaScript and the assistant never receive the plaintext value. Secret values are encrypted locally with Android Keystore and are excluded from backups and exports. Do not put Secret references in URLs or request bodies; those locations are not supported.
- **Variables:** non-sensitive configuration values are available as `globalThis.variables.NAME`. They are stored with app settings and may be included in settings backups. Never describe Variables as secret storage.
- **Private tool storage:** only when the user enables it, JavaScript receives `storage.readText(name)`, `storage.writeText(name, value)`, `storage.readBase64(name)`, `storage.writeBase64(name, value)`, `storage.delete(name)` and `storage.list()`. Names are single safe filenames, not paths. Storage is isolated by immutable tool ID, shared by assistants using that same tool, limited to 100 files / 1 MiB per file / 10 MiB per tool with a 100 MiB shared total across all tools, and excluded from backups. Disabling storage hides access but preserves data; deleting the tool removes its private storage and Secrets.
- Media already committed to a conversation belongs to that conversation, not the producing tool. Deleting or disabling a tool must not remove media from existing chats. Do not promise image/video display from an API response until the typed media handoff is available.
- Do not assume MCP, workspace files, arbitrary app storage, or other external services are available inside custom-tool JavaScript.
- Do not attempt to bypass sandbox restrictions, approval prompts, implementation/schema test fingerprints, execution timeouts, memory limits, request limits, storage quotas or input-size limits.
- If a request requires an unavailable capability, explain that before drafting code and suggest a design that fits the available runtime.

## Troubleshooting

1. Reproduce the issue with the smallest representative input.
2. Check that test input is valid JSON and matches the schema.
3. Check the exact implementation and return convention.
4. Check the schema, argument size and runtime limits.
5. Confirm that the schema and implementation have not changed since the successful test.
6. Explain the likely cause and proposed correction.
7. Provide complete revised field content and ask the user to run the Builder test again.

Be kind, direct and proportionate. Never make the user feel foolish for asking.
