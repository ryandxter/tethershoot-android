---
trigger: always_on
description: Auto-integrates AAS Core, AAS Plugins, and Automatic Parallel Subagent Spawning into OpenGSD (/gsd) workflows across AGY IDE, AGY CLI, AGY Agentic, and Claude CLI.
---

# AAS Core & Plugin Auto-Integration + Auto Parallel Subagents Rule for OpenGSD

When executing any OpenGSD workflow (`/gsd`, `/gsd-next`, `/gsd-plan-phase`, `/gsd-execute-phase`, `/gsd-ui-phase`, `/gsd-secure-phase`, `/gsd-discuss-phase`, `/gsd-onboard`, `/gsd-map-codebase`, etc.):

1. **Automatic Tech Stack & Domain Inspection:**
   Identify the project's technology stack (React, Next.js, Vue, Node, Express, Python, FastAPI, Go, Docker, AWS, SQL, Security, AI/MCP, etc.).

2. **Auto-Load Relevant AAS Plugins & Skills:**
   - **Frontend & Design:** Activate `agentic-bundle-aas-web-app-builder` & `agentic-bundle-aas-product-design-studio` (`frontend-developer`, `frontend-design`, `ui-ux-pro-max`, `react-best-practices`, `nextjs-best-practices`, `tailwind-patterns`, `shadcn`).
   - **Backend & APIs:** Activate `agentic-bundle-aas-api-platform-builder` & `agentic-bundle-aas-python-api-builder` (`backend-dev-guidelines`, `api-design`, `openapi-contracts`).
   - **Security & Auth:** Activate `agentic-bundle-aas-security-engineer` & `agentic-bundle-aas-secure-app-builder` (`security-audit`, `threat-modeling`, `owasp-top-10`).
   - **AI & MCP Systems:** Activate `agentic-bundle-aas-agent-mcp-builder` & `agentic-bundle-aas-ai-product-evaluation-ops` (`google-antigravity-sdk`, `mcp-server-builder`, `ai-evals`).
   - **DevOps & Cloud:** Activate `agentic-bundle-aas-devops-cloud` & `agentic-bundle-aas-observability-ir` (`dockerfile-optimization`, `terraform-best-practices`, `ci-cd-pipelines`).
   - **Data & Databases:** Activate `agentic-bundle-aas-data-analytics` & `agentic-bundle-aas-data-engineering-platform` (`sql-optimization`, `schema-migration`).
   - **QA & Testing:** Activate `agentic-bundle-aas-qa-test-automation` (`test-pyramid-strategy`, `playwright-e2e`).
   - **SaaS & Revenue:** Activate `agentic-bundle-aas-saas-launch-revenue` & `agentic-bundle-aas-marketing-seo-growth` (`stripe-integration`, `seo-audit`).
   - **Mobile:** Activate `agentic-bundle-aas-mobile-app-builder` (`mobile-developer`, `mobile-design`).

3. **OpenGSD Lifecycle Integration:**
   - **Planning (`/gsd-plan-phase`):** Incorporate the appropriate AAS playbooks into `PLAN.md` tasks.
   - **Execution (`/gsd-execute-phase`):** Execute implementation following AAS patterns.
   - **UI Design (`/gsd-ui-phase`):** Apply AAS high-end visual design and accessibility guidelines.
   - **Security Audit (`/gsd-secure-phase`):** Evaluate security against AAS security engineer standards.
   - **Testing (`/gsd-add-tests`):** Use AAS QA playbooks for comprehensive test suites.

4. **AUTOMATIC PARALLEL SUBAGENT SPAWNING:**
   - **Always Spawn Parallel Subagents:** For any phase execution, codebase mapping, research, code review, UI design, or security audit, NEVER execute sequentially inline when subagents can run in parallel.
   - **AGY IDE, AGY CLI & AGY Agentic:** Use native subagent dispatch (`invoke_subagent` / `define_subagent`) to launch specialized subagents concurrently for independent tasks.
   - **Claude CLI:** Use native subagent spawning (`Agent()`) in parallel waves.
   - **Configuration Defaults:** Force `parallelization: true`, `workflow.inline_plan_threshold: 0`, `workflow.use_worktrees: true`, and `worktree.baseRef: "head"`.
