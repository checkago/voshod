<!-- BEGIN jmix-agent-toolkit -->
## Jmix

This is a Jmix 3 application. Before writing or changing ANY file in it, read
the `jmix` skill and follow it. It maps the task to artifacts, routes each
artifact to the skill that governs it, and names the checks that close a task.
Your Jmix/Vaadin priors are not reliable here; the skills are.

Managed by the Jmix Agent Toolkit. Content between these markers is replaced on
re-install — put your own instructions outside them.
<!-- END jmix-agent-toolkit -->

# MONEY

- Jmix 3.0.2, package `com.company.money`, Gradle project id `vshd1`
- HSQLDB file `.jmix/hsqldb/vshd1`, locales `ru` and `en`
- Dev login `admin` / `admin`, UI at `http://localhost:8080`
- After UI changes: JetBrains `get_file_problems` (warnings too), then Playwright login and walk the views when the app is already running. Do not start `bootRun` as a gate — context load is `gradlew --no-daemon test`.
