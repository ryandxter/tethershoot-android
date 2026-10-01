# Subagent Summoning & Spawning Protocol (No Generic 'self')

## 1. Strict Prohibition on Generic `self` Spawning
- Spawning subagents with `TypeName: "self"` is **STRICTLY PROHIBITED** for all OpenGSD workflows (`/gsd`, `/gsd-new-project`, `/gsd-new-milestone`, `/gsd-plan-phase`, `/gsd-execute-phase`, `/gsd-map-codebase`, `/gsd-code-review`, `/gsd-ui-phase`, `/gsd-secure-phase`, `/gsd-debug`, etc.) and AAS domain tasks.
- Generic clones lack specialized persona boundaries, claim provenance tracking, and deep domain skills.

## 2. Mandatory Pre-Planning & Milestone Research Subagents
To prevent domain blind spots, breaking dependencies, and technical debt accumulation:
1. **Milestone Roadmapping (`/gsd-new-project`, `/gsd-new-milestone`):**
   - Must summon `gsd-project-researcher` and `gsd-assumptions-analyzer` to produce `.planning/research/` (`SUMMARY.md`, `STACK.md`, `ARCHITECTURE.md`, `PITFALLS.md`).
   - Must summon `gsd-roadmapper` to structure phase dependencies based on research findings.
2. **Phase Pre-Planning (`/gsd-plan-phase`, `/gsd-spec-phase`):**
   - Must summon `gsd-phase-researcher`, `gsd-domain-researcher`, `gsd-ui-researcher`, and `gsd-research-synthesizer` to create `.planning/phases/XX-name/RESEARCH.md` before `gsd-planner` creates `PLAN.md`.
   - All factual claims must carry source provenance tags (`[VERIFIED: ...]`, `[CITED: ...]`, `[ASSUMED]`).

## 3. Subagent Definition & Invocation Protocol
Whenever a subagent is required:
1. Match the specialized persona from `/Users/chiio/.agents/agents/subagents/`:
   - Research & Roadmapping: `gsd-project-researcher`, `gsd-assumptions-analyzer`, `gsd-roadmapper`, `gsd-phase-researcher`, `gsd-domain-researcher`, `gsd-ui-researcher`, `gsd-research-synthesizer`
   - Mapping & Planning: `gsd-codebase-mapper`, `gsd-planner`, `gsd-plan-checker`
   - Execution & Verification: `gsd-executor`, `gsd-verifier`, `gsd-code-reviewer`, `gsd-debugger`, `gsd-debug-session-manager`
   - Design & Auditing: `gsd-ui-auditor`, `gsd-security-auditor`, `gsd-nyquist-auditor`, `gsd-doc-writer`
2. Combine with relevant AAS skill guidelines (e.g. `deep-research`, `threat-modeling`, `architecture-patterns`, `react-best-practices`, `ui-ux-pro-max`, `api-design`).
3. Define the subagent first via `define_subagent` with rich instructions, persona boundaries, and explicit write permissions.
4. Invoke via `invoke_subagent` using the declared `TypeName` (never `self`), descriptive `Role`, and actionable `Prompt`.

## 4. Mandatory Quality Gate & Lifecycle Skill Injections
Never launch lifecycle subagents with bare generic instructions. Always inject the corresponding AAS domain playbooks:
- **Systematic Debugging (`/gsd-debug` / `gsd-debugger`):** Inject `systematic-debugging` (Iron Law: No fixes without root cause reproduction), `debugging-toolkit`, `error-detective`, `invariant-guard`, `test-fixing`, `phase-gated-debugging`, and `poka-yoke`. Forbid trial-and-error symptom patching (e.g. empty try/catch, ungrounded `?.`, blind sleep timers).
- **Code Review (`/gsd-code-review` / `gsd-code-reviewer`):** Inject `code-reviewer`, `architect-review`, `differential-review`, `react-best-practices`, `nextjs-best-practices`, `security-audit`, `simplify-code`, `debate-review`, `case-review`, and `babysit-pr`.
- **Code Fixes (`/gsd-code-review-fix` / `gsd-code-fixer`):** Inject `clean-code`, `clean-code-guard`, `tdd-workflows-tdd-cycle`, `poka-yoke`, and `simplify-code`.
- **UI Spec & Pre-Planning (`/gsd-ui-phase` / `gsd-ui-researcher`):** Inject `ui-ux-pro-max`, `frontend-design`, `marketing-psychology`, `tailwind-patterns`, `shadcn`, and `mobile-design`.
- **UI Visual & A11y Audit (`/gsd-ui-review` / `gsd-ui-auditor`):** Inject `web-design-guidelines`, `ui-a11y`, `wcag-audit-patterns`, `ui-motion`, `design-spells`, `anti-ui-slop`, and `mobile-design`.
- **Security & Reverse Audit (`/gsd-secure-phase` / `gsd-security-auditor`):** Inject `security-audit`, `threat-modeling`, `owasp-top-10`, `api-security`, `database-security`, `cloud-k8s`, `attack-chain`, `binary-diff`, and `api-security-best-practices`.
- **Nyquist Validation & Quality Gates (`/gsd-validate-phase` / `gsd-nyquist-auditor`):** Inject `unit-testing-test-generate`, `test-pyramid-strategy`, `invariant-guard`, `tdd-workflows-tdd-red`, `falsify`, `spec-driven-loop`, and `poka-yoke`.
- **AI Evaluation & Agent QA (`/gsd-eval-review` / `gsd-eval-auditor` / `gsd-eval-planner`):** Inject `agentic-bundle-aas-ai-product-evaluation-ops`, `ai-evals`, `agent-evaluation`, `agent-evaluation-reporting`, `agent-qa-authoring`, `agent-qa-debug-fix`, `agent-qa-result-triage`, `audit-agent-run-evidence`, `multi-agent-orchestration-review`, `agent-harness-fault-injection`, and `prompt-caching`.
- **Cross-Phase Integration & Resilience (`gsd-integration-checker` / `gsd-executor`):** Inject `agentic-bundle-aas-api-platform-builder`, `openapi-contracts`, `api-design-principles`, `graceful-shutdown`, `api-rate-limit-handler`, `schema-migration`, and `distributed-tracing`.
- **AI Systems Research & Fine-Tuning (`gsd-ai-researcher`):** Inject `agentic-bundle-aas-agent-mcp-builder`, `google-antigravity-sdk`, `mcp-server-builder`, `rag-engineer`, `unsloth-finetuning`, and `SandBase MCP`. **MANDATORY:** Dynamically explore and verify current active production model IDs per execution; reject static base models.
- **Architectural Tradeoff Advisor (`gsd-advisor-researcher` / `gsd-framework-selector`):** Inject `system-architecture`, `deep-research`, `risk-manager`, `c4-container`, `project-state-governor`, and `compile-knowledge`. Must probe current active provider model tiers (Google, OpenAI, Anthropic, Meta) dynamically before recommending models.
- **Autonomous Multi-Phase Orchestrator (`/gsd-autonomous` / `aas-squad-orchestrator`):** Inject `subagent-orchestrator`, `agent-squad`, `workflow-orchestration-patterns`, `dispatching-parallel-agents`, `parallel-agents`, and cross-agent delegation skills (`claude-delegate`, `cursor-delegate`, `cline-delegate`, `codex-delegate`, `aider-delegate`). Perform DAG dependency analysis on `ROADMAP.md` to identify independent phases and dispatch concurrent execution waves instead of forcing rigid serial execution.
- **QA & Testing (`/gsd-add-tests` / `aas-qa-engineer`):** Inject `agentic-bundle-aas-qa-test-automation`, `test-pyramid-strategy`, `playwright-e2e`, and `unit-testing-test-generate`.
- **Docs & ADRs (`/gsd-docs-update` / `gsd-doc-writer`):** Inject `brain-to-docs`, `c4-container`, `architecture-decision-records`, and `api-documentation-generator`.
- **Forensics & Incident (`/gsd-forensics` / `gsd-forensics-investigator`):** Inject `agenttrace-session-audit` and `resolving-merge-conflicts`.

