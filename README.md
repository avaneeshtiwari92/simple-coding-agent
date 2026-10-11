# Simple Coding Agent

A small Java learning project that builds a terminal-based coding agent with the official OpenAI Java SDK and the Responses API.

It currently includes two incremental examples:

- `Chat` — a multi-turn terminal conversation with OpenAI.
- `Read` — the same conversation loop plus a `read_file` function tool that allows the model to inspect files inside the current working directory.

## Prerequisites

- Java 17
- An OpenAI API key

## Configure credentials

Create an API key in the OpenAI Platform, then export it in the terminal session that runs the application:

```zsh
export OPENAI_API_KEY="sk-your-api-key"
```

Optionally choose a different model:

```zsh
export OPENAI_MODEL="gpt-5.2"
```

Never commit API keys, `.env` files containing keys, or other credentials to this repository.

## Build

```zsh
./mvnw package
```

## Run the examples

Start the basic chat client:

```zsh
./mvnw -q compile exec:java \
  -Dexec.mainClass=org.example.simplecodingagent.Chat \
  -Dexec.args="--verbose"
```

Start the file-reading agent:

```zsh
./mvnw -q compile exec:java \
  -Dexec.mainClass=org.example.simplecodingagent.Read \
  -Dexec.args="--verbose"
```

Enter a prompt such as `Read pom.xml and summarize the dependencies.` Use `Ctrl-C` to end the session.

## File-reading safety

`Read` exposes only one function tool: `read_file`. It resolves every requested path from the current working directory and rejects paths outside it, including paths reached through symlinks. It also rejects directories and reads only regular files.

## Project layout

```text
src/main/java/org/example/simplecodingagent/
├── Chat.java     # OpenAI terminal chat client
└── Read.java     # OpenAI terminal chat client with read_file tool
```

## Reference

This project follows the progressive workshop structure from [ghuntley/how-to-build-a-coding-agent](https://github.com/ghuntley/how-to-build-a-coding-agent?ref=ghuntley.com), adapted from Go and Anthropic examples to Java and OpenAI's official SDK.
