# 2026-08-23 — Topology details panel opacity

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | React Infrastructure map |
| Type | Bugfix / UX |
| Status | Done |

## Context

Clicking a topology node opens a right-hand details drawer.

## Problem

The global `MuiDrawer` theme is built for the left nav: transparent paper, 270px, `whiteSpace: nowrap`. The inspector inherited that, so the map showed through the panel and the text was unreadable.

## Design

Keep the global nav drawer unchanged. Override only the topology inspector: temporary drawer, solid white paper, 420px, dimmed backdrop, close control, labeled sections, text buttons for View component / View incident / Analyze with AI.

## Implementation (files)

| File | Role |
| --- | --- |
| `frontend/src/pages/topology/TopologyDetailsDrawer.tsx` | Opaque inspector panel |

## Tests

ESLint on the drawer file: pass. No new backend tests.

## Result / example

The details panel is an opaque white sheet over a dimmed map. Operational and security remain separate sections.

## Out of scope

Changing the left navigation drawer theme.

## Verify commands

Open `/topology`, click a component node, confirm the panel is opaque and the Close / View component actions work.

## Rapport talking points

Theme defaults for navigation must not leak into operational inspector panels.
