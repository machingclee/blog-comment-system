---
name: commentsystem-system-backend
description: The user is building a special service in the backend — the commentsystem-system blog commentsystem API (Java 25 + Spring Boot 4, deployed to AWS Lambda SnapStart via Serverless Framework v4, PostgreSQL). Use when working on or discussing the backend commentsystem service, when the user mentions "the special service", or when making changes under commentsystem-system/.
---

# Comment System Backend — The Special Service

The user is building a **special service in the backend**: the `comment-system` blog comment API. Treat this as a
flagship piece of work — the user cares about its quality.

## What it is

- **Location:** `comment-system/`
- **Stack:** Java **25** + Spring Boot **4** web API, runs locally via Maven and deploys to **AWS Lambda (SnapStart)**
  behind REST API Gateway using the **Serverless Framework v4**
- **Storage:** PostgreSQL via Spring Data JPA
- **Package:** `com.machingclee.entity.blogcomment`
- Key pieces: `CommentController` (REST endpoints), `ApiResponse` envelope, `@LogRequest` / `@LogQuery` AOP logging,
  `LambdaHandler` (SpringBootLambdaContainerHandler)

## Directories

The whole project lives under `/Users/chingcheonglee/Repos/Javascript/machingclee.github.io.source/app/` and contains
both the **frontend** and the **Prisma** project:

- **Frontend:** `app/` itself — the React + Vite blog web app (`src/`, `vite.config.ts`, `index.html`)
- **Prisma:** `app/comment-system/db/` — Prisma 7 schema + migration project (`prisma.config.ts`,
  `prisma/schema.prisma`), PostgreSQL `blog-system` schema; npm scripts `create` / `deploy` / `apply` via `.env.prod`
- **Backend (this special service):** `app/comment-system/` — the Spring Boot API

## Common commands

```bash
cd commentsystem-system
mvn spring-boot:run        # local dev, then curl http://localhost:8080/ping
npm run deploy             # dev (serverless.yml)
npm run deploy:prod        # prod (serverless-prod.yml)
```

## Guidelines

- When the user talks about "the special service" or "the backend service", they mean this `comment-system` API.
- Keep its conventions intact: API responses wrapped in `ApiResponse`, controllers annotated with `@LogRequest`, package
  layout under `com.machingclee.entity.blogcomment`.
- Deployment goes through Serverless Framework v4 with the Docker Maven package hook (builds `target/function.jar`).
