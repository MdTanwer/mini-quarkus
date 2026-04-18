package com.tanwir.miniquarkus.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

import com.tanwir.miniquarkus.generator.BeanInfo.DeploymentInfo;
import com.tanwir.miniquarkus.generator.ResourceOutput.Resource;

/**
 * Complete integration example showing how to use Quarkus-style bytecode generation
 * in a real application context. This demonstrates the full workflow from bean discovery
 * to class generation and loading.
 */
public class QuarkusBytecodeIntegration {

    private final ReflectionRegistration reflectionRegistration;
    private final Predicate<DotName> applicationClassPredicate;
    private final Set<String> existingClasses;
    private final Set<DotName> singleContextScopes;
    private final Map<String, Class<?>> generatedClasses = new ConcurrentHashMap<>();

    public QuarkusBytecodeIntegration() {
        this.reflectionRegistration = new IntegrationReflectionRegistration();
        this.applicationClassPredicate = this::isApplicationClass;
        this.existingClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.singleContextScopes = new HashSet<>(Arrays.asList(
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"),
            DotName.createSimple("jakarta.enterprise.context.RequestScoped"),
            DotName.createSimple("jakarta.enterprise.context.SessionScoped")
        ));
    }

    /**
     * Main integration method that processes a bean and generates all necessary classes.
     */
    public void processBean(BeanInfo bean) {
        System.out.println("Processing bean: " + bean.getBeanClass());

        // 1. Generate bean class
        generateBeanClass(bean);

        // 2. Generate client proxy if needed
        if (needsClientProxy(bean)) {
            generateClientProxy(bean);
        }

        // 3. Generate subclass if needed
        if (needsSubclass(bean)) {
            generateSubclass(bean);
        }

        System.out.println("Bean processing completed for: " + bean.getBeanClass());
    }

    private void generateBeanClass(BeanInfo bean) {
        BeanGenerator generator = new BeanGenerator(true, reflectionRegistration,
                                                  applicationClassPredicate, existingClasses);
        
        generator.precomputeGeneratedName(bean);
        List<Resource> resources = (List<Resource>) generator.generate(bean);
        
        for (Resource resource : resources) {
            loadGeneratedClass(resource);
        }
    }

    private void generateClientProxy(BeanInfo bean) {
        ClientProxyGenerator generator = new ClientProxyGenerator(true, true, reflectionRegistration,
                                                                  applicationClassPredicate, existingClasses,
                                                                  singleContextScopes);
        
        List<Resource> resources = (List<Resource>) generator.generate(bean, bean.getBeanClass().toString());
        
        for (Resource resource : resources) {
            loadGeneratedClass(resource);
        }
    }

    private void generateSubclass(BeanInfo bean) {
        SubclassGenerator generator = new SubclassGenerator(true, reflectionRegistration,
                                                           applicationClassPredicate, existingClasses);
        
        List<Resource> resources = (List<Resource>) generator.generate(bean, bean.getBeanClass().toString());
        
        for (Resource resource : resources) {
            loadGeneratedClass(resource);
        }
    }

    private void loadGeneratedClass(Resource resource) {
        try {
            // In a real Quarkus application, this would use the proper classloader
            String className = resource.getName();
            byte[] bytecode = resource.getData();
            
            System.out.println("Loading generated class: " + className);
            System.out.println("  - Size: " + bytecode.length + " bytes");
            System.out.println("  - Type: " + resource.getSpecialType());
            
            // Store the generated class info
            generatedClasses.put(className, createMockClass(className));
            existingClasses.add(className);
            
        } catch (Exception e) {
            System.err.println("Failed to load generated class: " + resource.getName());
            e.printStackTrace();
        }
    }

    private boolean needsClientProxy(BeanInfo bean) {
        // Client proxies are needed for normal scopes
        return !isApplicationScope(bean.getScope());
    }

    private boolean needsSubclass(BeanInfo bean) {
        // Subclasses are needed for intercepted beans
        return hasInterceptors(bean);
    }

    private boolean isApplicationClass(DotName className) {
        return className.toString().startsWith("com.example");
    }

    private boolean isApplicationScope(DotName scope) {
        return DotName.createSimple("jakarta.enterprise.context.ApplicationScoped").equals(scope);
    }

    private boolean hasInterceptors(BeanInfo bean) {
        // Simplified - in real implementation this would check for interceptor bindings
        return false;
    }

    private Class<?> createMockClass(String className) {
        // Create a mock class for demonstration
        return new MockClass(className);
    }

    /**
     * Demonstrates the complete workflow with multiple beans.
     */
    public void demonstrateCompleteWorkflow() {
        System.out.println("=== Complete Quarkus Bytecode Generation Workflow ===\n");

        // Create deployment info
        DeploymentInfo deploymentInfo = new DeploymentInfo(true, null);

        // Create sample beans with different characteristics
        List<BeanInfo> beans = createSampleBeans(deploymentInfo);

        // Process each bean
        for (BeanInfo bean : beans) {
            processBean(bean);
            System.out.println();
        }

        // Show summary
        System.out.println("=== Generation Summary ===");
        System.out.println("Total generated classes: " + generatedClasses.size());
        System.out.println("Generated classes:");
        for (String className : generatedClasses.keySet()) {
            System.out.println("  - " + className);
        }

        // Show reflection registration summary
        if (reflectionRegistration instanceof IntegrationReflectionRegistration) {
            IntegrationReflectionRegistration reg = (IntegrationReflectionRegistration) reflectionRegistration;
            System.out.println("\n=== Reflection Registration ===");
            System.out.println("Registered methods:");
            System.out.println(reg.getRegisteredMethods());
            System.out.println("Registered fields:");
            System.out.println(reg.getRegisteredFields());
        }
    }

    private List<BeanInfo> createSampleBeans(DeploymentInfo deploymentInfo) {
        List<BeanInfo> beans = new ArrayList<>();

        // Application scoped bean
        beans.add(createBean("com.example.ApplicationService", 
                           "jakarta.enterprise.context.ApplicationScoped", deploymentInfo));

        // Request scoped bean
        beans.add(createBean("com.example.RequestService", 
                           "jakarta.enterprise.context.RequestScoped", deploymentInfo));

        // Session scoped bean
        beans.add(createBean("com.example.SessionService", 
                           "jakarta.enterprise.context.SessionScoped", deploymentInfo));

        return beans;
    }

    private BeanInfo createBean(String className, String scope, DeploymentInfo deploymentInfo) {
        DotName beanClass = DotName.createSimple(className);
        Type providerType = Type.create(beanClass, org.jboss.jandex.Type.Kind.CLASS);
        
        Set<Type> types = new HashSet<>();
        types.add(providerType);
        types.add(Type.create(DotName.createSimple(className + "Interface"), org.jboss.jandex.Type.Kind.CLASS));
        
        Set<DotName> qualifiers = new HashSet<>();
        qualifiers.add(DotName.createSimple(scope));
        
        DotName scopeDotName = DotName.createSimple(scope);
        
        return new BeanInfo(beanClass, providerType, types, qualifiers, scopeDotName, 
                          deploymentInfo, className.toLowerCase());
    }

    /**
     * Mock class for demonstration purposes.
     */
    private static class MockClass extends ClassLoader {
        private final String className;

        public MockClass(String className) {
            this.className = className;
        }

        @Override
        public String toString() {
            return "MockClass[" + className + "]";
        }
    }

    /**
     * Integration-specific reflection registration that tracks all registrations.
     */
    private static class IntegrationReflectionRegistration implements ReflectionRegistration {
        private final StringBuilder registeredMethods = new StringBuilder();
        private final StringBuilder registeredFields = new StringBuilder();
        private final Map<String, String> clientProxies = new HashMap<>();
        private final Map<String, String> subclasses = new HashMap<>();

        @Override
        public void registerMethod(String declaringClass, String name, String... params) {
            registeredMethods.append("  ").append(declaringClass).append(".").append(name);
            registeredMethods.append("(").append(String.join(", ", params)).append(")\n");
        }

        @Override
        public void registerMethod(org.jboss.jandex.MethodInfo methodInfo) {
            registeredMethods.append("  ").append(methodInfo.declaringClass().name())
                            .append(".").append(methodInfo.name()).append("\n");
        }

        @Override
        public void registerField(org.jboss.jandex.FieldInfo fieldInfo) {
            registeredFields.append("  ").append(fieldInfo.declaringClass().name())
                           .append(".").append(fieldInfo.name()).append("\n");
        }

        @Override
        public void registerClientProxy(DotName beanClassName, String clientProxyName) {
            clientProxies.put(beanClassName.toString(), clientProxyName);
        }

        @Override
        public void registerSubclass(DotName beanClassName, String subclassName) {
            subclasses.put(beanClassName.toString(), subclassName);
        }

        public String getRegisteredMethods() {
            return registeredMethods.length() > 0 ? registeredMethods.toString() : "  (none)";
        }

        public String getRegisteredFields() {
            return registeredFields.length() > 0 ? registeredFields.toString() : "  (none)";
        }

        public Map<String, String> getClientProxies() {
            return clientProxies;
        }

        public Map<String, String> getSubclasses() {
            return subclasses;
        }
    }

    public static void main(String[] args) {
        QuarkusBytecodeIntegration integration = new QuarkusBytecodeIntegration();
        integration.demonstrateCompleteWorkflow();
    }
}
