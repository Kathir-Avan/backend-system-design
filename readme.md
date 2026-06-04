# Spring Boot Production-Grade Product Management API

A production-style Spring Boot REST API demonstrating layered architecture, DTO isolation, DAO pattern, JPA/Hibernate switching via profiles, Flyway migrations, validation, exception handling, and soft deletes.

---

## Project Architecture

```text
springboot-production/
├── pom.xml
└── src/main/java/com/production/app/
    ├── ProductionApplication.java
    │
    ├── model/
    │   └── Product.java
    │
    ├── dto/
    │   ├── ProductRequestDTO.java
    │   ├── ProductResponseDTO.java
    │   ├── ApiResponse.java
    │   └── ProductMapper.java
    │
    ├── repository/
    │   └── ProductRepository.java
    │
    ├── dao/
    │   ├── ProductDAO.java
    │   ├── jpa/
    │   │   └── JpaProductDAO.java
    │   └── hibernate/
    │       └── HibernateProductDAO.java
    │
    ├── service/
    │   ├── ProductService.java
    │   └── impl/
    │       └── ProductServiceImpl.java
    │
    ├── controller/
    │   └── ProductController.java
    │
    ├── exception/
    │   ├── ResourceNotFoundException.java
    │   ├── DuplicateResourceException.java
    │   └── GlobalExceptionHandler.java
    │
    └── config/
        └── WebConfig.java
```

---

## Technology Stack

* Java 21
* Spring Boot
* Spring Data JPA
* Hibernate
* MySQL
* Flyway
* MapStruct
* Maven
* Bean Validation (Jakarta Validation)

---

## Prerequisites

* Java 21+
* Maven 3.9+
* MySQL 8+
* Git

---

## Database Setup

Create the database:

```bash
mysql -u root -p -e "CREATE DATABASE production_db;"
```

Configure database credentials in:

```properties
src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/production_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=yourpassword
```

Flyway automatically:

* Creates the schema
* Creates the products table
* Seeds sample data during first startup

---

## Running the Application

### Run with JPA (Default)

```bash
mvn spring-boot:run
```

or

```bash
mvn spring-boot:run -Dspring.profiles.active=dev
```

### Run with Native Hibernate DAO

```bash
mvn spring-boot:run -Dspring.profiles.active=hibernate
```

Using profiles swaps the DAO implementation without changing any business logic, controllers, or DTOs.

---

## API Endpoints

| Method | Endpoint                                     | Description               |
| ------ | -------------------------------------------- | ------------------------- |
| POST   | `/api/v1/products`                           | Create Product            |
| GET    | `/api/v1/products/{id}`                      | Get Product By ID         |
| GET    | `/api/v1/products/sku/{sku}`                 | Get Product By SKU        |
| GET    | `/api/v1/products`                           | List Products (Paginated) |
| GET    | `/api/v1/products?category=Electronics`      | Filter By Category        |
| GET    | `/api/v1/products?minPrice=100&maxPrice=500` | Filter By Price Range     |
| GET    | `/api/v1/products?search=laptop`             | Search Products By Name   |
| GET    | `/api/v1/products/low-stock?threshold=5`     | Inventory Alert           |
| PUT    | `/api/v1/products/{id}`                      | Update Product            |
| DELETE | `/api/v1/products/{id}`                      | Soft Delete Product       |

---

## Sample Request

### Create Product

```bash
curl -X POST http://localhost:8080/api/v1/products \
-H "Content-Type: application/json" \
-d '{
  "sku":"MOUSE-001",
  "name":"Logitech G502",
  "price":79.99,
  "stockQuantity":50,
  "category":"Accessories"
}'
```

### Sample Response

```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "sku": "MOUSE-001",
    "name": "Logitech G502",
    "price": 79.99,
    "stockQuantity": 50,
    "category": "Accessories"
  }
}
```

---

## Production Patterns Demonstrated

### DAO Pattern

The service layer depends only on the `ProductDAO` interface.

Different implementations can be activated using Spring Profiles:

* `JpaProductDAO`
* `HibernateProductDAO`

This allows implementation changes without affecting business logic.

---

### DTO Isolation

Three separate models are maintained:

* Product Entity (Persistence Layer)
* ProductRequestDTO (API Input)
* ProductResponseDTO (API Output)

MapStruct generates mapping code at compile time with no reflection overhead.

---

### Soft Deletes

Products are never physically removed.

Instead:

```java
product.setActive(false);
```

All read operations automatically filter:

```java
active = true
```

This preserves historical data and supports auditing.

---

### Monetary Values

Prices are represented using:

```java
BigDecimal
```

Database type:

```sql
DECIMAL(10,2)
```

This prevents floating-point rounding issues.

---

### Validation Strategy

Validation occurs at multiple layers:

#### API Layer

```java
@NotBlank
@NotNull
@Positive
```

#### Service Layer

Business validation rules.

#### Database Layer

```sql
UNIQUE (sku)
```

This provides defense-in-depth validation.

---

### Flyway Database Migrations

Schema changes are managed using Flyway.

Example:

```text
V1__init_schema.sql
```

Benefits:

* Version-controlled database changes
* Consistent schema across environments
* Automated startup migrations

---

## Profiles

| Profile   | DAO Implementation  |
| --------- | ------------------- |
| dev       | JpaProductDAO       |
| default   | JpaProductDAO       |
| prod      | JpaProductDAO       |
| hibernate | HibernateProductDAO |

---

## Future Enhancements

* Swagger / OpenAPI Documentation
* JWT Authentication
* Redis Caching
* Docker Support
* Kubernetes Deployment
* CI/CD Pipeline
* Audit Logging
* Micrometer & Prometheus Monitoring

---

## Author

Production-grade Spring Boot reference project demonstrating enterprise application architecture and best practices.
