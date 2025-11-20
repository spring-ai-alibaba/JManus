# User Module

This module provides simple user management functionality following the adapter pattern used in the Lynxe project.

## Structure

```
user/
├── controller/          # REST API endpoints
│   └── UserController.java
├── model/              # Data models
│   └── po/             # Persistent objects (JPA entities)
│       └── UserEntity.java
├── repository/         # JPA repositories
│   └── UserRepository.java
├── service/            # Business logic
│   ├── UserService.java
│   └── UserDataInitializer.java
└── vo/                 # Value objects (data models)
    ├── User.java
    └── UserResponse.java
```

## Features

- **Simple User Management**: Basic user data without complex authentication or authorization
- **JPA Entity**: User entity with database persistence support
- **Repository Pattern**: JPA repository following the same pattern as prompt package
- **Data Initialization**: Automatic creation of default users on startup
- **Predefined User**: One default user for testing purposes
- **Query Operations**: Search and retrieve user information
- **REST API**: Standard HTTP endpoints for user operations

## API Endpoints

### User Operations
- `GET /api/v1/users` - Get all users
- `GET /api/v1/users/{id}` - Get user by ID
- `GET /api/v1/users/username/{username}` - Get user by username
- `GET /api/v1/users/email/{email}` - Get user by email
- `GET /api/v1/users/active` - Get active users only
- `GET /api/v1/users/search?displayName={name}` - Search users by display name

### Utility Endpoints
- `GET /api/v1/users/statistics` - Get user statistics
- `GET /api/v1/users/{id}/exists` - Check if user exists
- `GET /api/v1/users/username/{username}/available` - Check username availability
- `GET /api/v1/users/email/{email}/available` - Check email availability
- `GET /api/v1/users/health` - Health check

## Default User

The service comes with one predefined user:
- **ID**: 1 (Long)
- **Username**: lynxe_user
- **Email**: user@lynxe.ai
- **Display Name**: Lynxe User
- **Status**: active

## Database Schema

The User entity maps to the following database tables:

### users table
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `username` (VARCHAR, UNIQUE, NOT NULL)
- `email` (VARCHAR, UNIQUE, NOT NULL)
- `display_name` (VARCHAR)
- `created_at` (DATETIME, NOT NULL)
- `last_login` (DATETIME)
- `status` (VARCHAR, NOT NULL)

### user_preferences table
- `user_id` (BIGINT, FOREIGN KEY)
- `preference` (VARCHAR)

## JPA Implementation

The user module follows the same JPA patterns as the prompt package:

### Repository Layer
- `UserRepository` extends `JpaRepository<UserEntity, Long>`
- Custom query methods for username, email, and status-based searches
- Existence checks for username and email uniqueness

### Entity Layer
- `UserEntity` in `model.po` package (Persistent Object)
- JPA annotations for database mapping
- Unique constraints on username and email
- Element collection for user preferences

### Service Layer
- `UserService` uses repository for all database operations
- `UserDataInitializer` creates default users on application startup
- Mapping between Entity and VO objects using `BeanUtils`

### Value Objects
- `User` and `UserResponse` in `vo` package for API responses
- Clean separation between persistence and presentation layers

## Usage

The user module is designed to be simple and focused on basic query operations. It does not include:
- User authentication
- Password management
- Role-based access control
- User registration/modification
- Admin management features

This keeps the implementation simple and focused on the core requirement of providing basic user data access.
