<!-- GSD Configuration — managed by gsd-core installer -->
# GSD Core — Git. Ship. Done. + AAS Autonomous Multi-Agent Orchestration

- **GSD Workflows:** Workflows live in `~/.agents/gsd-core/workflows/`. Load the relevant workflow when running `/gsd-*` commands (`/gsd`, `/gsd-next`, `/gsd-plan-phase`, `/gsd-execute-phase`, `/gsd-autonomous`, `/gsd-ui-phase`, `/gsd-debug`, etc.).
- **Specialized Subagent Spawning (STRICT NO-GENERIC-SELF):** Summon and define matching specialized agents (`gsd-autonomous-orchestrator`, `gsd-project-researcher`, `gsd-phase-researcher`, `gsd-roadmapper`, `gsd-assumptions-analyzer`, `gsd-codebase-mapper`, `gsd-planner`, `gsd-executor`, `gsd-verifier`, `gsd-code-reviewer`, `gsd-debugger`, `gsd-ui-auditor`, `gsd-security-auditor`, `aas-product-designer`, etc.) via `define_subagent`. NEVER spawn generic `self`.
- **AAS Core & Plugin Auto-Injection:** Auto-inject AAS core and domain plugin playbooks (`agentic-bundle-aas-*`, `clean-code-guard`, `tdd-workflow`, `systematic-debugging`, `deep-research`, `subagent-orchestrator`, `agent-squad`, `ui-ux-pro-max`, `frontend-design`) into subagent system prompts matching the task domain.
- **Figma MCP Direct Integration:** Figma Developer MCP (`get_figma_data`, `download_figma_images`) is active and authenticated globally. When a Figma URL is provided in `/gsd-ui-phase`, `/gsd-sketch`, or UI execution, extract design tokens, layout hierarchy, and download SVG/PNG assets directly.
- **DAG Parallel Wave Orchestration:** In `/gsd`, `/gsd-next`, and `/gsd-autonomous`, detect non-conflicting roadmap phases and execute them in parallel waves instead of rigid serial locking.
- **Graphify Mandatory Exploration:** Check `graphify-out/graph.json` before reading raw files. Query using `graphify query "<concept>"`. Run `graphify update .` after modifications.
- **Tools:** GSD tools are at `~/.gemini/antigravity/gsd-core/bin/gsd-tools.cjs` or `~/.agents/gsd-core/bin/gsd-tools.cjs`. Run with `node`.
- **Planning Artifacts:** Planning artifacts live in `.planning/`. Never edit them outside a GSD workflow.
<!-- End GSD Configuration -->
