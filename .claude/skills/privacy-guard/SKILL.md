---
name: privacy-guard
description: Check text before it is published from this repository (commits on a push, pull request descriptions, issues, comments) for personal or process information. Use before any push or GitHub post. The PreToolUse hook in .claude/settings.json runs this mission on every push and GitHub post.
---

# Privacy guard

This repository is public. Everything pushed or posted to GitHub can be read, copied and indexed by anyone, and it stays visible after a later edit. Your job is to read one outgoing publication and decide whether it exposes something about the people who work on the repository, their machines or how they work.

Judge the meaning, not the shape of a string. A word that looks like a path or a name is a leak only when it points to something private. Block only a real leak, and say exactly which text leaks and why.

## What must not be published

- **Personal identity.** Real names, email addresses, phone numbers and street addresses of people. The GitHub login of the repository owner and the GitHub handles of upstream authors are public and may appear.
- **The author's machine.** Absolute paths outside the repository, such as a home folder, a temporary folder of a tool or a session's scratch folder. Host names, user names and IP addresses of private machines.
- **Working sessions.** Links to chat or agent sessions, and session or conversation identifiers. The two attribution trailers below are the only exception.
- **How the work was produced.** Which AI model or agent wrote, reviewed or fixed something; budgets or costs of a session; handoff notes, prompts, worktrees, pauses, "overnight" runs and review passes done by agents. A pull request explains the change, its reasons and its verification. It never explains the process that produced it.
- **Secrets.** Tokens, keys, passwords and private URLs that grant access.
- **Details that help an attacker.** A step-by-step account of how to exploit a vulnerability that is not fixed yet.

## What is fine

- Code, tests, build files and paths inside the repository.
- Technical content about USE, OCL, Kodkod, the plugins and their bugs, including exact error messages.
- Issue and pull request numbers, commit hashes and public URLs of this project and its upstream projects.
- Mentions of tools as tools, such as Claude Code as the editor used, a CI job or a SAT solver.
- These attribution lines, exactly as written, and only these: a `Co-Authored-By: Claude ... <noreply@anthropic.com>` commit trailer, a `Claude-Session: https://claude.ai/code/session_...` commit trailer, and the pull request footer `🤖 Generated with [Claude Code](https://claude.com/claude-code)` followed by one session link.
- This file and other documentation that describe these categories in general terms.

## Verdict

Answer with your reasons first, in a few sentences. End with exactly one last line:

- `PASS` when nothing in the publication leaks.
- `BLOCK: <the leaking text, quoted> - <why it is private>` when something leaks. Name every leak, separated by `; `.

## Use it by hand

Before you post text that the hook does not see, read it against this mission yourself. The hook covers `git push` and the GitHub tools listed in `.claude/settings.json`.
