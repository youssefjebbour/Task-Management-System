# TaskFlow design review

## Direction

The supplied `insp/inspiration.png` remains the main reference. The design-taste-frontend app workflow selected **Framed sheet**, seed **1280114459**, with the supplied palette/type direction retained. No redraws were used. The face is a cool, pale navigation frame around a white working sheet, local DM Sans, blue underline selections and restrained semantic task colors. The signature is the task worklist: ownership, deadline and state stay together, with a right-side inspector for the details.

All displayed task/report values are computed from the API and PostgreSQL. The visible development examples belong to Website redesign, Mobile experience and Design system. Seed people and tasks are development-only; replace them for real use. No external photography, illustrations, stock media or invented growth statistics are used. The TaskFlow mark is a local SVG; screenshots are captures of the running application.

## Final critique

ROUND 3

Seen in one second: a focused workspace where assigned work, priority and deadlines are the main content.

Idea (one sentence): keep the next action close to the task while making project responsibility visible.

Live thing: database-backed worklists and a task drawer; edits, comments, labels, attachments and board movement persist through the Spring API.

Layout words by section: shell=quiet frame / worklist=aligned rows / overview=metric strip and evidence / board=state lanes / settings=label-and-control columns / detail=side drawer.

Scores (editorial assessment, not benchmark results): design 8 · usability 8.5 · creativity 7 · content 8.5 → overall 8.0.

Caps applied: app-screen criteria; landing-page imagery and motion caps do not apply.

Top three problems reviewed, with screenshot and resolution:

1. `my-tasks-desktop.png` / `my-tasks-390.png`: small status/priority/ownership labels competed with compact metadata. Working labels now use 13px and mobile task titles 14px, with 12px secondary project/key text. The final inspector retains one phone small-text warning (47% under 13px), primarily repeated metadata and avatar initials. This is retained to preserve a compact multi-field task list; the warning is not suppressed.
2. Inspector tablet page capture: footer words were too close to the edge and below the intended contrast threshold. Increased the footer inset and darkened the faint text token; both findings cleared. Removed the resting navigation-item shadow.
3. `settings.png`: a broad descendant selector changed avatar text color/size, reducing contrast. Scoped it to the email text. Also made the profile link's accessible name include its visible initials and added radio-group keyboard navigation. Automated accessibility checks pass on the tested states.

The remaining visual tradeoff is density: long task names truncate in table cells and become fully available in the drawer; narrow phones show one Kanban lane with horizontal board scrolling. Small-account screens intentionally retain empty space instead of filling it with invented data. A manual screen-reader/device audit would provide coverage beyond the automated checks.

## Screen questions

| Screen | Question it answers |
| --- | --- |
| Login | How do I return to my work? |
| Registration | How do I create an account? |
| Recovery/reset | How do I regain access safely? |
| My Tasks | What is assigned to me, and what needs attention? |
| Overview | How is my work distributed and what is due? |
| Notifications | What changed that concerns me? |
| Calendar | What is due on a particular day? |
| Completed | What work have I finished? |
| Projects | Which workstreams can I access? |
| Project Overview | What is the goal, ownership and progress? |
| Board | Which state is the work in? |
| Project List | Which task matches my filters? |
| Members | Who belongs to this project and what can they do? |
| Project Settings | What project policy or date needs changing? |
| Team | Who works with me and how are project teams organized? |
| Reports | Where is remaining work concentrated? |
| Admin Users | Who has access and system privileges? |
| Profile/Settings | What personal or appearance detail needs changing? |
| Audit | Who changed an important record or permission? |
| Search | Where is a task across my accessible projects? |

## Checks performed

- Inspected the primary worklist at 1440, 768 and 390 pixels: **0 FAIL, 1 WARN**. Opened every fold/full-page image produced by the final inspection.
- Captured and reviewed auth, workspace, project, admin, drawer and dark-theme screens. Calendar and board have additional phone captures. The capture script reports no browser errors or horizontal page overflow.
- Source style lint: **0 FAIL, 7 WARN**. One warning is ISO-date zero-padding; the other six are shadows on actual dialogs, drawer, toast, drag preview and open mobile navigation. Those uses are intentional and were not hidden from the checker.
- Six Docker-backed browser tests pass, including real CRUD/collaboration, file transfer, drag/drop, email recovery, read-only viewer behavior, tab refresh, theme controls and navigation.
- Axe WCAG A/AA checks pass for the tested login, worklist, overview, settings, dark and phone states. This is an automated check, not a claim of complete accessibility conformance.

Run `node frontend/scripts/capture.mjs` with the seeded application running to refresh the documentation screenshots. The script reads the local development password without printing it. Playwright tests similarly use the real local services and clean up their temporary projects; recovery accounts are deactivated afterward.
