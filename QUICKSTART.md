# Quick Start Guide

## 🚀 Create Your First Mini Quarkus Project

### Using Maven Plugin (Recommended)

```bash
# Create a new project
mvn com.tanwir:mini-quarkus-maven-plugin:create-project \
  -DartifactId=my-first-app \
  -DgroupId=com.example \
  -DclassName=HelloResource

# Navigate to your project
cd my-first-app

# Build the application
./mvnw package

# Run your application
java -jar target/my-first-app.jar
```

### Test Your Application

Open your browser and visit:
- http://localhost:8080/hello
- http://localhost:8080/hello/YourName

## 📁 Project Structure

```
my-first-app/
├── pom.xml                    # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/
│   │   │       ├── GreetingService.java    # Business logic
│   │   │       └── HelloResource.java      # REST endpoints
│   │   └── resources/
│   └── test/
│       └── java/
│           └── com/example/
│               └── HelloResourceTest.java  # Tests
└── target/
    └── my-first-app.jar      # Executable JAR
```

## 🎯 What You Get

**Generated Features:**
- ✅ **Dependency Injection**: `@Singleton` and `@Inject` support
- ✅ **REST Endpoints**: `@GetInvoker` for HTTP routes
- ✅ **Build-Time Processing**: Fast startup with annotation processing
- ✅ **Executable JAR**: Single file deployment
- ✅ **Logging**: JBoss logging integration
- ✅ **Testing**: JUnit 5 test framework

**Key Classes:**
- `GreetingService`: Business logic with DI
- `HelloResource`: REST endpoints with injection
- `HelloResourceTest`: Unit tests

## 🛠️ Development Workflow

### 1. Add New Endpoints

```java
@GetInvoker("/users/{id}")
public String getUser(String id) {
    return "User: " + id;
}
```

### 2. Add New Services

```java
@Singleton
public class UserService {
    public String findUser(String id) {
        return "User-" + id;
    }
}
```

### 3. Build and Run

```bash
# Compile with annotation processing
./mvnw compile

# Package as executable JAR
./mvnw package

# Run the application
java -jar target/my-first-app.jar
```

## 🔧 Customization Options

### Different Package Names

```bash
mvn com.tanwir:mini-quarkus-maven-plugin:create-project \
  -DartifactId=my-app \
  -DgroupId=com.mycompany \
  -DpackageName=com.mycompany.myapp \
  -DclassName=MyResource
```

### Add Dependencies to pom.xml

```xml
<dependency>
    <groupId>com.tanwir</groupId>
    <artifactId>mini-quarkus-arc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 📚 Learning Path

1. **Start Here**: Understand the generated code
2. **Read Core**: Study `independent-projects/arc/` for DI
3. **Study Bootstrap**: Learn `independent-projects/bootstrap/` for startup
4. **Compare**: Compare with actual Quarkus source code
5. **Extend**: Add your own features and extensions

## 🆘 Need Help?

- **Documentation**: Check the main README.md
- **Examples**: Look at `integration-tests/main/`
- **Source Code**: Study the implementation in `independent-projects/`
- **Issues**: Report problems on GitHub

## 🎉 Next Steps

Once you're comfortable with the basics:
1. **Add Gizmo**: Implement bytecode generation
2. **Configuration**: Add properties support
3. **Extensions**: Create your own extensions
4. **Performance**: Benchmark against Quarkus

Happy coding with Mini Quarkus! 🚀
