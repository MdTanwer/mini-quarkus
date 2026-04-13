package com.tanwir.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates a new Mini Quarkus project from templates
 */
@Mojo(name = "create-project", defaultPhase = LifecyclePhase.NONE, requiresDirectInvocation = true)
public class CreateProjectMojo extends AbstractMojo {

    /**
     * The project artifactId
     */
    @Parameter(property = "artifactId", required = true)
    private String artifactId;

    /**
     * The project groupId
     */
    @Parameter(property = "groupId", defaultValue = "com.example")
    private String groupId;

    /**
     * The project version
     */
    @Parameter(property = "version", defaultValue = "1.0.0-SNAPSHOT")
    private String version;

    /**
     * The main resource class name
     */
    @Parameter(property = "className", defaultValue = "GreetingResource")
    private String className;

    /**
     * The package for the resource class
     */
    @Parameter(property = "packageName")
    private String packageName;

    /**
     * Output directory
     */
    @Parameter(defaultValue = "${basedir}", readonly = true)
    private File outputDirectory;

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Creating Mini Quarkus project: " + artifactId);
        
        // Validate inputs
        validateInputs();
        
        // Create project directory
        Path projectDir = Paths.get(outputDirectory.getAbsolutePath(), artifactId);
        createProjectDirectory(projectDir);
        
        // Generate project structure
        generateProjectStructure(projectDir);
        
        // Generate POM
        generatePom(projectDir);
        
        // Generate source files
        generateSourceFiles(projectDir);
        
        // Generate test files
        generateTestFiles(projectDir);
        
        getLog().info("Mini Quarkus project '" + artifactId + "' created successfully!");
        getLog().info("Next steps:");
        getLog().info("  cd " + artifactId);
        getLog().info("  ./mvnw compile");
        getLog().info("  ./mvnw package");
        getLog().info("  java -jar target/" + artifactId + ".jar");
    }

    private void validateInputs() throws MojoExecutionException {
        if (artifactId == null || artifactId.trim().isEmpty()) {
            throw new MojoExecutionException("artifactId is required");
        }
        
        Path projectDir = Paths.get(outputDirectory.getAbsolutePath(), artifactId);
        if (Files.exists(projectDir)) {
            throw new MojoExecutionException("Directory '" + artifactId + "' already exists");
        }
        
        if (packageName == null) {
            packageName = groupId + "." + artifactId.replace("-", "");
        }
    }

    private void createProjectDirectory(Path projectDir) throws MojoExecutionException {
        try {
            Files.createDirectories(projectDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create project directory", e);
        }
    }

    private void generateProjectStructure(Path projectDir) throws MojoExecutionException {
        try {
            // Create Maven directory structure
            Files.createDirectories(projectDir.resolve("src/main/java/" + packageName.replace('.', '/')));
            Files.createDirectories(projectDir.resolve("src/main/resources"));
            Files.createDirectories(projectDir.resolve("src/test/java/" + packageName.replace('.', '/')));
            Files.createDirectories(projectDir.resolve("src/test/resources"));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create project structure", e);
        }
    }

    private void generatePom(Path projectDir) throws MojoExecutionException {
        String pomContent = generatePomContent();
        Path pomFile = projectDir.resolve("pom.xml");
        
        try {
            Files.writeString(pomFile, pomContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate pom.xml", e);
        }
    }

    private String generatePomContent() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "\n" +
                "    <groupId>" + groupId + "</groupId>\n" +
                "    <artifactId>" + artifactId + "</artifactId>\n" +
                "    <version>" + version + "</version>\n" +
                "    <packaging>jar</packaging>\n" +
                "\n" +
                "    <properties>\n" +
                "        <maven.compiler.source>17</maven.compiler.source>\n" +
                "        <maven.compiler.target>17</maven.compiler.target>\n" +
                "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                "        <mini-quarkus.version>1.0.0-SNAPSHOT</mini-quarkus.version>\n" +
                "    </properties>\n" +
                "\n" +
                "    <dependencies>\n" +
                "        <dependency>\n" +
                "            <groupId>com.tanwir</groupId>\n" +
                "            <artifactId>mini-quarkus-arc</artifactId>\n" +
                "            <version>${mini-quarkus.version}</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>com.tanwir</groupId>\n" +
                "            <artifactId>mini-quarkus-resteasy-reactive</artifactId>\n" +
                "            <version>${mini-quarkus.version}</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>com.tanwir</groupId>\n" +
                "            <artifactId>mini-quarkus-bootstrap-runner</artifactId>\n" +
                "            <version>${mini-quarkus.version}</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.jboss.logging</groupId>\n" +
                "            <artifactId>jboss-logging</artifactId>\n" +
                "            <version>3.5.3.Final</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.vertx</groupId>\n" +
                "            <artifactId>vertx-web</artifactId>\n" +
                "            <version>4.5.8</version>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n" +
                "\n" +
                "    <build>\n" +
                "        <plugins>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-compiler-plugin</artifactId>\n" +
                "                <version>3.11.0</version>\n" +
                "                <configuration>\n" +
                "                    <annotationProcessorPaths>\n" +
                "                        <path>\n" +
                "                            <groupId>com.tanwir</groupId>\n" +
                "                            <artifactId>mini-quarkus-arc-processor</artifactId>\n" +
                "                            <version>${mini-quarkus.version}</version>\n" +
                "                        </path>\n" +
                "                        <path>\n" +
                "                            <groupId>com.tanwir</groupId>\n" +
                "                            <artifactId>mini-quarkus-resteasy-reactive-processor</artifactId>\n" +
                "                            <version>${mini-quarkus.version}</version>\n" +
                "                        </path>\n" +
                "                    </annotationProcessorPaths>\n" +
                "                </configuration>\n" +
                "            </plugin>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-shade-plugin</artifactId>\n" +
                "                <version>3.5.1</version>\n" +
                "                <executions>\n" +
                "                    <execution>\n" +
                "                        <phase>package</phase>\n" +
                "                        <goals>\n" +
                "                            <goal>shade</goal>\n" +
                "                        </goals>\n" +
                "                        <configuration>\n" +
                "                            <transformers>\n" +
                "                                <transformer implementation=\"org.apache.maven.plugins.shade.resource.ManifestResourceTransformer\">\n" +
                "                                    <mainClass>com.tanwir.bootstrap.runner.MiniQuarkusEntryPoint</mainClass>\n" +
                "                                </transformer>\n" +
                "                            </transformers>\n" +
                "                        </configuration>\n" +
                "                    </execution>\n" +
                "                </executions>\n" +
                "            </plugin>\n" +
                "        </plugins>\n" +
                "    </build>\n" +
                "</project>\n";
    }

    private void generateSourceFiles(Path projectDir) throws MojoExecutionException {
        // Generate service class
        generateServiceClass(projectDir);
        
        // Generate resource class
        generateResourceClass(projectDir);
    }

    private void generateServiceClass(Path projectDir) throws MojoExecutionException {
        String serviceContent = generateServiceContent();
        Path serviceFile = projectDir.resolve("src/main/java/" + 
            packageName.replace('.', '/') + "/GreetingService.java");
        
        try {
            Files.writeString(serviceFile, serviceContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate service class", e);
        }
    }

    private String generateServiceContent() {
        return "package " + packageName + ";\n" +
                "\n" +
                "import com.tanwir.arc.Singleton;\n" +
                "\n" +
                "@Singleton\n" +
                "public class GreetingService {\n" +
                "    \n" +
                "    public String greeting(String name) {\n" +
                "        return \"Hello, \" + name + \"!\";\n" +
                "    }\n" +
                "}\n";
    }

    private void generateResourceClass(Path projectDir) throws MojoExecutionException {
        String resourceContent = generateResourceContent();
        Path resourceFile = projectDir.resolve("src/main/java/" + 
            packageName.replace('.', '/') + "/" + className + ".java");
        
        try {
            Files.writeString(resourceFile, resourceContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate resource class", e);
        }
    }

    private String generateResourceContent() {
        return "package " + packageName + ";\n" +
                "\n" +
                "import com.tanwir.arc.Inject;\n" +
                "import com.tanwir.resteasy.reactive.server.GetInvoker;\n" +
                "import org.jboss.logging.Logger;\n" +
                "\n" +
                "public class " + className + " {\n" +
                "    \n" +
                "    private static final Logger LOG = Logger.getLogger(" + className + ".class);\n" +
                "    \n" +
                "    @Inject\n" +
                "    GreetingService greetingService;\n" +
                "    \n" +
                "    @GetInvoker(\"/hello\")\n" +
                "    public String hello() {\n" +
                "        LOG.info(\"Processing hello request\");\n" +
                "        return greetingService.greeting(\"Mini Quarkus\");\n" +
                "    }\n" +
                "    \n" +
                "    @GetInvoker(\"/hello/{name}\")\n" +
                "    public String helloName(String name) {\n" +
                "        LOG.info(\"Processing hello request for: \" + name);\n" +
                "        return greetingService.greeting(name);\n" +
                "    }\n" +
                "}\n";
    }

    private void generateTestFiles(Path projectDir) throws MojoExecutionException {
        String testContent = generateTestContent();
        Path testFile = projectDir.resolve("src/test/java/" + 
            packageName.replace('.', '/') + "/" + className + "Test.java");
        
        try {
            Files.writeString(testFile, testContent);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate test class", e);
        }
    }

    private String generateTestContent() {
        return "package " + packageName + ";\n" +
                "\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n" +
                "\n" +
                "public class " + className + "Test {\n" +
                "    \n" +
                "    @Test\n" +
                "    public void testGreetingService() {\n" +
                "        GreetingService service = new GreetingService();\n" +
                "        String result = service.greeting(\"World\");\n" +
                "        assertEquals(\"Hello, World!\", result);\n" +
                "    }\n" +
                "}\n";
    }
}
