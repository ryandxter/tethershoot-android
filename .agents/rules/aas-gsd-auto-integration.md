---
trigger: always_on
description: Auto-integrates AAS Core, AAS Plugins, Dynamic Model Exploration, and Automatic Parallel Subagent Spawning into OpenGSD (/gsd) workflows across AGY IDE, AGY CLI, AGY Agentic, and Claude CLI.
---

# AAS Core & Plugin Auto-Integration + Dynamic Model Exploration + Parallel Subagents Rule for OpenGSD

When executing any OpenGSD workflow (`/gsd`, `/gsd-next`, `/gsd-plan-phase`, `/gsd-execute-phase`, `/gsd-ui-phase`, `/gsd-secure-phase`, `/gsd-discuss-phase`, `/gsd-onboard`, `/gsd-map-codebase`, `/gsd-ai-integration-phase`, etc.):

1. **Automatic Tech Stack & Domain Inspection:**
   Identify the project's technology stack (React, Next.js, Vue, Node, Express, Python, FastAPI, Go, Docker, AWS, SQL, Security, AI/MCP, etc.).

2. **MANDATORY DYNAMIC MODEL EXPLORATION (/gsd-explore Mindset):**
   - For all AI/LLM integration tasks (especially `/gsd-ai-integration-phase`, agent tool construction, prompt engineering, and evaluation):
   - **NEVER** use static base models or outdated lower-tier fallbacks from memory or legacy examples.
   - **PER-EXECUTION LIVE PROBE:** Always dynamically explore the currently valid, state-of-the-art flagship and utility models across providers (Google Gemini 2.5/3.x Pro & Flash, Anthropic Claude 3.7/3.5 Sonnet & Haiku, OpenAI o-series/GPT-4o, Meta Llama 3.3/4.x) and runtime environment.
   - Lock verified model identifiers, parameter limits, and tier rationales into design specifications.

3. **Auto-Load Relevant AAS Plugins & Skills:**
   - **Frontend & Design:** Activate `agentic-bundle-aas-web-app-builder` & `agentic-bundle-aas-product-design-studio` (`frontend-developer`, `frontend-design`, `ui-ux-pro-max`, `react-best-practices`, `nextjs-best-practices`, `tailwind-patterns`, `shadcn`).
   - **Backend & APIs:** Activate `agentic-bundle-aas-api-platform-builder` & `agentic-bundle-aas-python-api-builder` (`backend-dev-guidelines`, `api-design`, `openapi-contracts`, `api-rate-limit-handler`).
   - **Security & Auth:** Activate `agentic-bundle-aas-security-engineer` & `agentic-bundle-aas-secure-app-builder` (`security-audit`, `threat-modeling`, `owasp-top-10`, `poka-yoke`).
   - **AI & MCP Systems:** Activate `agentic-bundle-aas-agent-mcp-builder` & `agentic-bundle-aas-ai-product-evaluation-ops` (`google-antigravity-sdk`, `mcp-server-builder`, `ai-evals`, `rag-engineer`, `unsloth-finetuning`).
   - **DevOps & Cloud:** Activate `agentic-bundle-aas-devops-cloud` & `agentic-bundle-aas-observability-ir` (`dockerfile-optimization`, `terraform-best-practices`, `ci-cd-pipelines`, `graceful-shutdown`).
   - **Data & Databases:** Activate `agentic-bundle-aas-data-analytics` & `agentic-bundle-aas-data-engineering-platform` (`sql-optimization`, `schema-migration`).
   - **QA & Testing:** Activate `agentic-bundle-aas-qa-test-automation` (`test-pyramid-strategy`, `playwright-e2e`, `tdd-workflow`, `falsify`, `spec-driven-loop`).
   - **Dialectics & Debate:** Activate AAS Debater (`debate-review`, `crossframe-debate`, `anti-sycophancy`, `falsify`, `poka-yoke`, `clean-code-guard`) for `/gsd-debater`, `/gsd-capture --thoughts`, and post-execution thought extraction.
   - **SaaS & Revenue:** Activate `agentic-bundle-aas-saas-launch-revenue` & `agentic-bundle-aas-marketing-seo-growth` (`stripe-integration`, `seo-audit`).
   - **Mobile:** Activate `agentic-bundle-aas-mobile-app-builder` (`mobile-developer`, `mobile-design`).

4. **OpenGSD Lifecycle Integration:**
   - **Planning (`/gsd-plan-phase`):** Incorporate the appropriate AAS playbooks into `PLAN.md` tasks.
   - **AI Integration (`/gsd-ai-integration-phase`):** Enforce dynamic model exploration before framework and model selection.
   - **Execution (`/gsd-execute-phase`):** Execute implementation following AAS clean code and TDD patterns.
   - **Debater & Thought Harvesting (`/gsd-debater`, `/gsd-capture --thoughts`):** Harvest agent thought streams, doubts, and trade-offs into structured debate records and micro-notes.
   - **UI Design (`/gsd-ui-phase`):** Apply AAS high-end visual design and accessibility guidelines.
   - **Security Audit (`/gsd-secure-phase`):** Evaluate security against AAS security engineer standards.
   - **Testing (`/gsd-add-tests`):** Use AAS QA playbooks for comprehensive test suites.

5. **AUTOMATIC PARALLEL SUBAGENT SPAWNING:**
   - **Always Spawn Parallel Subagents:** For any phase execution, codebase mapping, research, code review, UI design, or security audit, NEVER execute sequentially inline when subagents can run in parallel.
   - **AGY IDE, AGY CLI & AGY Agentic:** Use native subagent dispatch (`invoke_subagent` / `define_subagent`) to launch specialized subagents concurrently for independent tasks.
   - **Claude CLI:** Use native subagent spawning (`Agent()`) in parallel waves.
   - **Configuration Defaults:** Force `parallelization: true`, `workflow.inline_plan_threshold: 0`, `workflow.use_worktrees: true`, and `worktree.baseRef: "head"`.
