package com.tanwir.miniquarkus.generator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.jandex.DotName;

import com.tanwir.miniquarkus.generator.BeanInfo.DeploymentInfo;
import com.tanwir.miniquarkus.generator.ResourceOutput.Resource;

/**
 * Comprehensive demonstration of Quarkus-style Bytecode Generation using Gizmo.
 * This shows the complete workflow from bean info to generated classes.
 */
public class BytecodeGenerationDemo {

    public static void main(String[] args) {
        System.out.println("=== Quarkus-Style Bytecode Generation Demo ===\n");

        // Create deployment info
        DeploymentInfo deploymentInfo = new DeploymentInfo(true, null);

        // Create a sample bean info
        BeanInfo sampleBean = createSampleBean(deploymentInfo);

        // Setup generators
        ReflectionRegistration reflectionRegistration = new TestReflectionRegistration();
        Predicate<DotName> applicationClassPredicate = dotName -> true;
        Set<String> existingClasses = Collections.emptySet();
        Set<DotName> singleContextScopes = new HashSet<>(Arrays.asList(
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"),
            DotName.createSimple("jakarta.enterprise.context.RequestScoped")
        ));

        // Generate bean class
        System.out.println("1. Generating Bean Class:");
        BeanGenerator beanGenerator = new BeanGenerator(true, reflectionRegistration, 
                                                    applicationClassPredicate, existingClasses);
        beanGenerator.precomputeGeneratedName(sampleBean);
        List<Resource> beanResources = (List<Resource>) beanGenerator.generate(sampleBean);
        printGeneratedResources(beanResources);

        // Generate client proxy
        System.out.println("\n2. Generating Client Proxy:");
        ClientProxyGenerator proxyGenerator = new ClientProxyGenerator(true, true, reflectionRegistration,
                                                                  applicationClassPredicate, existingClasses,
                                                                  singleContextScopes);
        List<Resource> proxyResources = (List<Resource>) proxyGenerator.generate(sampleBean, "com.example.SampleService");
        printGeneratedResources(proxyResources);

        // Generate subclass
        System.out.println("\n3. Generating Subclass:");
        SubclassGenerator subclassGenerator = new SubclassGenerator(true, reflectionRegistration,
                                                                   applicationClassPredicate, existingClasses);
        List<Resource> subclassResources = (List<Resource>) subclassGenerator.generate(sampleBean, "com.example.SampleService");
        printGeneratedResources(subclassResources);

        // Demonstrate advanced patterns
        System.out.println("\n4. Advanced Patterns Demonstration:");
        demonstrateAdvancedPatterns();

        System.out.println("\n=== Demo Complete ===");
    }

    private static BeanInfo createSampleBean(DeploymentInfo deploymentInfo) {
        DotName beanClass = DotName.createSimple("com.example.SampleService");
        Type providerType = Type.create(beanClass, org.jboss.jandex.Type.Kind.CLASS);
        
        Set<Type> types = new HashSet<>();
        types.add(providerType);
        types.add(Type.create(DotName.createSimple("com.example.SampleInterface"), org.jboss.jandex.Type.Kind.CLASS));
        
        Set<DotName> qualifiers = new HashSet<>();
        qualifiers.add(DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"));
        
        DotName scope = DotName.createSimple("jakarta.enterprise.context.ApplicationScoped");
        
        return new BeanInfo(beanClass, providerType, types, qualifiers, scope, deploymentInfo, "sampleService");
    }

    private static void printGeneratedResources(List<Resource> resources) {
        if (resources.isEmpty()) {
            System.out.println("   No resources generated (already exists)");
            return;
        }
        
        for (Resource resource : resources) {
            System.out.println("   Generated: " + resource.getName());
            System.out.println("   Type: " + resource.getType());
            System.out.println("   Special Type: " + resource.getSpecialType());
            System.out.println("   Application Class: " + resource.isApplicationClass());
            System.out.println("   Size: " + resource.getData().length + " bytes");
        }
    }

    private static void demonstrateAdvancedPatterns() {
        System.out.println("   Demonstrating advanced Quarkus bytecode generation patterns:");
        
        System.out.println("   - Lambda generation for functional interfaces");
        System.out.println("   - Exception handling with try-catch blocks");
        System.out.println("   - Collection operations (lists, sets, maps)");
        System.out.println("   - Reflection registration for native image");
        System.out.println("   - Bridge method generation for generics");
        System.out.println("   - Interceptor chain management");
        System.out.println("   - Context-aware proxy delegation");
        System.out.println("   - Lifecycle callback integration");
        System.out.println("   - Mock support in client proxies");
    }

    /**
     * Test implementation of ReflectionRegistration.
     */
    private static class TestReflectionRegistration implements ReflectionRegistration {
        private final StringBuilder registeredMethods = new StringBuilder();
        private final StringBuilder registeredFields = new StringBuilder();

        @Override
        public void registerMethod(String declaringClass, String name, String... params) {
            registeredMethods.append("Method: ").append(declaringClass).append(".").append(name);
            registeredMethods.append("(").append(String.join(", ", params)).append(")\n");
        }

        @Override
        public void registerMethod(org.jboss.jandex.MethodInfo methodInfo) {
            registeredMethods.append("MethodInfo: ").append(methodInfo.declaringClass().name())
                            .append(".").append(methodInfo.name()).append("\n");
        }

        @Override
        public void registerField(org.jboss.jandex.FieldInfo fieldInfo) {
            registeredFields.append("Field: ").append(fieldInfo.declaringClass().name())
                           .append(".").append(fieldInfo.name()).append("\n");
        }

        @Override
        public void registerClientProxy(DotName beanClassName, String clientProxyName) {
            registeredMethods.append("ClientProxy: ").append(beanClassName).append(" -> ").append(clientProxyName).append("\n");
        }

        @Override
        public void registerSubclass(DotName beanClassName, String subclassName) {
            registeredMethods.append("Subclass: ").append(beanClassName).append(" -> ").append(subclassName).append("\n");
        }

        public String getRegisteredMethods() {
            return registeredMethods.toString();
        }

        public String getRegisteredFields() {
            return registeredFields.toString();
        }
    }
}
