PHASE 1
Authentication runtime verification
Codex / Cline
        ↓

PHASE 2
Requester workflow
Codex / Cline
        ↓

PHASE 3
Admin + Department Head approval
Claude review
        ↓
Codex/Cline implementation
        ↓

PHASE 4
Work Orders
Kilo Architect
        ↓
Codex/Cline implementation
        ↓

PHASE 5
Technician Workflow
Kilo Architect
        ↓
Codex/Cline implementation
        ↓

PHASE 6
Notifications + History + Audit
Copilot/Cline
        ↓

PHASE 7
Python AI Integration
Claude/Kilo architecture
        ↓
Codex/Cline implementation
        ↓

PHASE 8
Dashboard + Reports
Copilot/Cline
        ↓

PHASE 9
Security
Claude/Amazon Q review
        ↓
Codex/Cline fixes
        ↓

PHASE 10
Automated Tests
Copilot/Gemini
        ↓

FINAL
Full end-to-end test


## Change-Control Rules

- Do not reformat, prettify, normalize, or rewrite entire existing files when making a localized change.
- Preserve the existing formatting and line structure.
- Modify only the minimum lines and files required for the current task.
- Do not modify unrelated modules.
- Do not create backup, temporary, corrupted, or step-copy files inside src/.
- Do not make speculative improvements or unrelated refactors.
- Fix only confirmed problems required by the current task.
- Do not bulk-fix SonarQube warnings.

Before editing:
1. Verify the workspace is C:\HTCServicePortal-CLEAN
2. Verify the branch is full-system-migration
3. Run git status --short
4. The working tree should be clean before starting a new phase.

After editing:
1. Review git diff --stat
2. Confirm only expected files changed.
3. Run mvn clean test
4. Run mvn clean package
5. Perform the required runtime test.
6. Stop if any command fails and report the first root error.
7. Do not automatically make additional fixes outside the current task.

Development workflow:
One small feature -> minimal change -> test -> package -> runtime verify -> commit -> stop.

Do not continue to another module until the current change is verified and committed.


## Windows Path Safety

The Windows user profile path contains a space:

C:\Users\Sean Keith

Never pass a path containing spaces without proper PowerShell quoting.

Prefer:
- $env:USERPROFILE
- Join-Path
- Set-Location -LiteralPath
- Get-Content -LiteralPath

Examples:

Correct:
Set-Location -LiteralPath "C:\Users\Sean Keith\Tools"

Correct:
$path = Join-Path $env:USERPROFILE "Tools\Maven"

Wrong:
cd C:\Users\Sean Keith\Tools

Never use these merely to inspect project files:
- start
- Start-Process
- Invoke-Item
- ii
- explorer
- cmd /c start

Do not launch/open a target named "Sean".

Use the agent's file-reading tools or PowerShell Get-Content -LiteralPath instead.