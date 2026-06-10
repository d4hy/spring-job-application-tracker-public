# Spring and Spring Boot Notes (Start to Finish)

## 1. What They Are
- Spring is a Java framework for building applications using dependency injection and modular architecture.
- Spring Boot is a layer on top of Spring that removes setup boilerplate and gives production-ready defaults.
- Spring gives flexibility. Spring Boot gives speed.

## 2. Why Use Them
- Build APIs and web apps quickly.
- Strong ecosystem for data, security, validation, and testing.
- Encourages clean architecture.
- High demand in backend job roles.

## 3. Prerequisites
- Java basics: OOP, interfaces, exceptions, collections, generics, streams.
- HTTP basics: methods, status codes, request/response.
- SQL basics: CRUD, joins, primary/foreign keys.
- Maven basics: clean, test, package.

## 4. Spring Core Concepts
- IoC: Spring controls object creation.
- DI: dependencies are injected, usually via constructor injection.
- Bean: object managed by Spring.
- ApplicationContext: container that creates and wires beans.
- AOP: cross-cutting concerns like logging, transactions, security.

## 5. Spring Boot Concepts
- Starter dependencies: spring-boot-starter-web, data-jpa, security.
- Auto-configuration: Boot configures defaults based on dependencies.
- Embedded server: run with built-in Tomcat/Jetty.
- Externalized config: application.properties plus environment variables.
- Actuator: health/metrics endpoints.

## 6. Standard Project Structure
- backend/src/main/java/com/example/jobtracker/
- backend/src/main/resources/
- backend/src/test/java/com/example/jobtracker/
- frontend/

Common package roles:
- config: security and framework config
- core/startup: startup seed/init logic
- feature/<module>/controller: HTTP endpoints
- feature/<module>/service: business logic
- feature/<module>/repository: repositories
- feature/<module>/model/entity: JPA models
- feature/<module>/model/dto: request/response models

## 7. Request Flow
1. Client sends HTTP request.
2. Controller receives request and validates input.
3. Controller calls service.
4. Service runs business rules.
5. Service calls repository.
6. Repository uses JPA/Hibernate for database operations.
7. Service returns result.
8. Controller returns response DTO.

## 8. Key Annotations (What They Mean)
- `@SpringBootApplication`: Main app entry annotation. Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`.
- `@Configuration`: Marks a class that defines framework/application bean configuration.
- `@Bean`: Declares a method that returns an object Spring should manage as a bean.
- `@EnableConfigurationProperties`: Binds typed config classes (for example JWT properties) to values from `application.properties`.
- `@Value("${...}")`: Injects one config value from properties/environment variables.
- `@Component`: Generic Spring-managed class.
- `@Service`: Specialization of `@Component` for business-logic classes.
- `@Repository`: Specialization of `@Component` for persistence/data-access classes; also enables exception translation for DB errors.
- `@RestController`: Marks a class as a REST API controller. Methods return response bodies (usually JSON) directly.
- `@RequestMapping`: Base URL mapping for a controller or method.
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`: HTTP method-specific route mappings.
- `@PathVariable`: Binds URL path segments (for example `/jobs/{id}`) to method parameters.
- `@RequestParam`: Binds query-string values (for example `?limit=10`) to method parameters.
- `@RequestBody`: Binds JSON request body to a Java object.
- `@Valid`: Triggers bean validation on request DTOs before method logic runs.
- `@NotBlank`, `@NotNull`, `@Size`: Common validation rules for DTO/entity fields.
- `@Entity`: Marks a class as a JPA database entity (table-backed model).
- `@Table`: Optional explicit database table name.
- `@Id`: Primary key field.
- `@GeneratedValue`: Primary key generation strategy (for example auto-increment identity).
- `@Column`: Optional column-level constraints/mapping details.
- `@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany`: JPA relationship mappings between entities.
- `@OrderBy`: Defines ordering of a related collection loaded by JPA.
- `@PrePersist`: Lifecycle callback executed right before first insert (often used for timestamps).
- `@Transactional`: Wraps method work in a database transaction (all succeed or all rollback).
- `@Transactional(readOnly = true)`: Transaction optimized for reads.
- `@ControllerAdvice`: Centralized exception/response handling across controllers.
- `@EventListener`: Runs a method on a Spring lifecycle/application event (for example on app ready).
- `@SpringBootTest`: Loads full Spring context for integration tests.
- `@AutoConfigureMockMvc`: Wires `MockMvc` for HTTP-layer testing without starting a real server.

## 9. Layer Responsibilities
- Controller: HTTP/API boundary.
- Service: business logic and rules.
- Repository: data access.
- DTO: API contract.
- Entity: persistence model.

### 9.1 What a Controller Is
- A controller is the class that handles incoming HTTP requests and returns HTTP responses.
- It maps URL routes (for example `/api/jobs`) to Java methods.
- It reads request input (`@PathVariable`, `@RequestParam`, `@RequestBody`).
- It validates input at the API boundary (often with `@Valid` and DTO constraints).
- It calls service methods to do real business work.
- It returns response bodies/status codes (`200`, `400`, `401`, etc.).

What should be in a controller:
- route definitions and request parsing
- simple response shaping (DTO mapping)
- HTTP status handling

What should not be in a controller:
- complex business rules
- direct repository/database logic
- long workflows/orchestration logic

## 10. JPA and Hibernate Essentials
- Repository usually extends JpaRepository<Entity, IdType>.
- Hibernate is the JPA implementation that executes SQL.
- Common relationships: @OneToMany, @ManyToOne, @OneToOne, @ManyToMany.
- Do not expose entities directly in API responses.
- Use migrations for schema evolution in real environments.

## 11. Validation and Error Handling
- Validate input at controller boundary with DTO constraints.
- Use @Valid for request bodies.
- Return clear error messages.
- Centralize API errors with @ControllerAdvice.

## 12. Spring Security Basics
- SecurityFilterChain defines public vs protected routes.
- Passwords must be hashed, usually with BCrypt.
- For APIs, stateless JWT auth is common.

JWT flow:
1. User logs in with username/password.
2. Server returns JWT.
3. Client sends Authorization: Bearer <token>.
4. Filter validates token and sets authenticated context.

## 13. Configuration and Profiles
- Keep secrets in environment variables, not hardcoded.
- Use profiles: dev, test, prod.
- Use profile-specific config files when needed.

## 14. Testing Strategy
- Unit tests for service logic.
- @WebMvcTest for controllers.
- @DataJpaTest for repository layer.
- @SpringBootTest for full integration only when necessary.

## 15. Build and Run Commands
- mvn clean
- mvn test
- mvn package
- mvn spring-boot:run
- java -jar target/<artifact>.jar

## 16. Production Checklist
- Secrets from environment/secret manager.
- Disable dev-only features in production.
- Strong JWT secret.
- Input validation on all endpoints.
- Logging/metrics and monitoring.
- Database migrations.

## 17. Common Mistakes
- Putting business logic inside controllers.
- Returning entities directly.
- Missing validation and authorization checks.
- Hardcoding secrets.
- Ignoring transaction boundaries.

## 18. 4-Week Learning Path
Week 1:
- Spring core, DI, and first REST endpoint.

Week 2:
- JPA entities/repositories and CRUD with DTOs.

Week 3:
- Spring Security with JWT auth.

Week 4:
- Testing, profiles, packaging, and hardening.

## 19. Mental Model
- Controller = HTTP adapter
- Service = business brain
- Repository = data access
- Entity = database model
- DTO = API contract
- Security filter = access gate

## 20. This Project Mapping
- Security config: `backend/src/main/java/com/example/jobtracker/config/SecurityConfig.java`
- JWT filter: `backend/src/main/java/com/example/jobtracker/config/JwtAuthenticationFilter.java`
- Startup/seed logic: `backend/src/main/java/com/example/jobtracker/core/startup`
- Feature services: `backend/src/main/java/com/example/jobtracker/feature/*/service`
- Feature repositories: `backend/src/main/java/com/example/jobtracker/feature/*/repository`
- Feature controllers: `backend/src/main/java/com/example/jobtracker/feature/*/controller`
- Feature DTOs: `backend/src/main/java/com/example/jobtracker/feature/*/model/dto`
- Feature entities: `backend/src/main/java/com/example/jobtracker/feature/*/model/entity`
