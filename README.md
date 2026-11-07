# E-Commerce REST API

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=flat-square&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![OpenAPI](https://img.shields.io/badge/API-OpenAPI_3-85EA2D?style=flat-square&logo=swagger&logoColor=black)](https://springdoc.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

A production-style e-commerce backend built with **Java 17** and **Spring Boot**. It includes JWT authentication with rotating refresh tokens, role-based catalog management, per-user shopping carts, transactional checkout, order lifecycle management, product search and filtering, PostgreSQL persistence, and OpenAPI documentation.

## Features

- JWT access tokens and rotating refresh tokens with reuse detection
- `USER` and `ADMIN` roles with endpoint-level authorization
- BCrypt password hashing and Bean Validation
- Product and category CRUD; catalog writes restricted to admins
- Product search, category/price/stock filters, sorting, and pagination
- Per-user carts with stock checks
- Atomic checkout with row-level stock locking
- Order cancellation and admin order-status transitions
- Consistent JSON error responses with field-level validation errors
- PostgreSQL schema managed by Flyway
- Swagger UI and OpenAPI JSON
- JUnit 5, MockMvc, and PostgreSQL integration tests
- Actuator health endpoint

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Security | Spring Security, JWT, BCrypt |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 16 |
| Migrations | Flyway |
| Documentation | Springdoc OpenAPI / Swagger UI |
| Testing | JUnit 5, MockMvc, Testcontainers |
| Build | Maven Wrapper |

## Project Structure

```text
.
├── README.md
└── ecommerce-api/
    ├── src/main/java/com/ecommerce/api/
    │   ├── config/          # CORS, OpenAPI, bootstrap configuration
    │   ├── controller/      # REST endpoints
    │   ├── dto/             # Request and response models
    │   ├── entity/          # JPA entities
    │   ├── exception/       # Domain exceptions and global error handling
    │   ├── mapper/          # Entity-to-DTO mapping
    │   ├── repository/      # Spring Data repositories
    │   ├── security/        # JWT, filters, security configuration
    │   ├── service/         # Business logic
    │   └── specification/   # Dynamic product queries
    ├── src/main/resources/
    │   ├── db/migration/    # Flyway migrations
    │   └── application*.yml
    └── src/test/            # MVC and PostgreSQL integration tests
```

## Getting Started

### Prerequisites

- JDK 17
- Docker Desktop or Docker Engine with Compose
- Git Bash, PowerShell, or a POSIX-compatible shell

### Run Locally

```bash
cd ecommerce-api
cp .env.example .env
docker compose up -d postgres
```

Spring Boot does not load `.env` automatically. Export the local database settings before starting the app:

```bash
export SPRING_PROFILES_ACTIVE=dev
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5433
export POSTGRES_DB=ecommerce
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=postgres

./mvnw spring-boot:run
```

On Windows PowerShell, use `$env:NAME = "value"` and `.\mvnw.cmd spring-boot:run`.

Flyway applies pending migrations at startup. Hibernate validates the entity mappings against the database schema.

The API is available at `http://localhost:8080`.

### Production Configuration

Run with the `prod` profile and provide the required values through environment variables or a secret manager:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host>:5432/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
JWT_SECRET=<at-least-32-byte-random-secret>
CORS_ALLOWED_ORIGINS=https://your-frontend.example
```

Use TLS at the ingress or reverse proxy. The development JWT secret and sample database password are for local use only.

## API

Detailed endpoint documentation and configuration notes are in [`ecommerce-api/README.md`](ecommerce-api/README.md).

With the application running:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

Product listing supports:

```text
GET /api/products?q=phone&categoryId=2&minPrice=10&maxPrice=100&minStock=1&page=0&size=20&sort=price,asc
```

The maximum page size is 100. Invalid input returns `400`; missing resources return `404`; unavailable stock and conflicting state return `409`.

## Tests

Run the unit and MVC tests:

```bash
cd ecommerce-api
./mvnw test
```

PostgreSQL integration tests are opt-in and require a separate test database. See the testing section of [`ecommerce-api/README.md`](ecommerce-api/README.md) for the exact commands and environment variables.

## License

This project is licensed under the [MIT License](LICENSE).
