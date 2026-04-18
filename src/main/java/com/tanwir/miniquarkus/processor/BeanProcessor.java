package com.tanwir.miniquarkus.processor;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;


import io.quarkus.arc.impl.BuiltinScope;

/**
 * BeanProcessor following Quarkus ARC patterns.
 * Central processor for analyzing beans and coordinating bytecode generation.
 */
public class BeanProcessor {

    private final IndexView index;
    private final Predicate<DotName> applicationClassPredicate;
    private final ReflectionRegistration reflectionRegistration;
    private final boolean transformPrivateInjectedFields;

    public BeanProcessor(IndexView index, Predicate<DotName> applicationClassPredicate,
                      ReflectionRegistration reflectionRegistration, boolean transformPrivateInjectedFields) {
        this.index = index;
        this.applicationClassPredicate = applicationClassPredicate;
        this.reflectionRegistration = reflectionRegistration;
        this.transformPrivateInjectedFields = transformPrivateInjectedFields;
    }

    /**
     * Processes all beans in the index and generates necessary bytecode.
     */
    public Collection<Resource> process() {
        Collection<Resource> allResources = new ArrayList<>();
        
        // Process all beans
        for (ClassInfo beanClass : index.getKnownClasses()) {
            if (shouldProcess(beanClass)) {
                BeanInfo beanInfo = createBeanInfo(beanClass);
                allResources.addAll(processBean(beanInfo));
            }
        }
        
        return allResources;
    }

    /**
     * Determines if a class should be processed.
     */
    private boolean shouldProcess(ClassInfo beanClass) {
        // Skip non-CDI classes
        if (!isCdiClass(beanClass)) {
            return false;
        }
        
        // Skip classes that shouldn't be processed
        if (isVetoed(beanClass)) {
            return false;
        }
        
        return true;
    }

    /**
     * Determines if a class is a CDI class.
     */
    private boolean isCdiClass(ClassInfo beanClass) {
        // Check for CDI annotations
        return hasAnnotation(beanClass, DotNames.NAMED) ||
               hasAnnotation(beanClass, DotNames.APPLICATION_SCOPED) ||
               hasAnnotation(beanClass, DotNames.NORMAL_SCOPED) ||
               hasAnnotation(beanClass, DotNames.DEPENDENT) ||
               hasAnnotation(beanClass, DotNames.PRODUCES) ||
               hasAnnotation(beanClass, DotNames.INJECT) ||
               hasAnnotation(beanClass, DotNames.INTERCEPTED) ||
               hasAnnotation(beanClass, DotNames.DECORATED);
    }

    /**
     * Determines if a class is vetoed.
     */
    private boolean isVetoed(ClassInfo beanClass) {
        return hasAnnotation(beanClass, DotNames.VETOED);
    }

    /**
     * Checks if a class has a specific annotation.
     */
    private boolean hasAnnotation(ClassInfo beanClass, DotName annotationName) {
        return beanClass.classAnnotation(annotationName) != null;
    }

    /**
     * Creates BeanInfo for a given class.
     */
    private BeanInfo createBeanInfo(ClassInfo beanClass) {
        // Create bean identifier
        String identifier = createBeanIdentifier(beanClass);
        
        // Determine bean types
        Set<Type> types = determineBeanTypes(beanClass);
        
        // Determine qualifiers
        Set<DotName> qualifiers = determineQualifiers(beanClass);
        
        // Determine scope
        DotName scope = determineScope(beanClass);
        
        // Create deployment info
        BeanInfo.DeploymentInfo deploymentInfo = new BeanInfo.DeploymentInfo(
            transformPrivateInjectedFields, beanClass);
        
        return new BeanInfo(beanClass.name(), Type.create(beanClass.name(), org.jboss.jandex.Type.Kind.CLASS),
                          types, qualifiers, scope, deploymentInfo, identifier);
    }

    /**
     * Creates a bean identifier.
     */
    private String createBeanIdentifier(ClassInfo beanClass) {
        String className = beanClass.name().toString();
        String packageName = DotNames.packagePrefix(beanClass.name());
        
        // Use simple name if no package
        if (packageName.isEmpty()) {
            return className.substring(className.lastIndexOf('.') + 1);
        }
        
        // Use package + simple name for packaged classes
        return packageName + "." + beanClass.simpleName();
    }

    /**
     * Determines the types of a bean.
     */
    private Set<Type> determineBeanTypes(ClassInfo beanClass) {
        Set<Type> types = new HashSet<>();
        
        // Always include the class itself
        types.add(Type.create(beanClass.name(), org.jboss.jandex.Type.Kind.CLASS));
        
        // Add implemented interfaces
        for (Type interfaceType : beanClass.interfaceTypes()) {
            types.add(interfaceType);
        }
        
        // Add superclass if not Object
        if (beanClass.superName() != null && !beanClass.superName().equals(DotNames.OBJECT)) {
            types.add(Type.create(beanClass.superName(), org.jboss.jandex.Type.Kind.CLASS));
        }
        
        return types;
    }

    /**
     * Determines the qualifiers of a bean.
     */
    private Set<DotName> determineQualifiers(ClassInfo beanClass) {
        Set<DotName> qualifiers = new HashSet<>();
        
        // Check for @Named
        AnnotationInstance named = beanClass.classAnnotation(DotNames.NAMED);
        if (named != null) {
            AnnotationValue value = named.value();
            if (value != null) {
                // Create qualifier with specific name
                qualifiers.add(createQualifierWithNamedValue(beanClass, value.asString()));
            } else {
                // Create default qualifier
                qualifiers.add(DotNames.NAMED);
            }
        }
        
        // Check for other qualifiers
        for (AnnotationInstance annotation : beanClass.classAnnotations()) {
            if (isQualifierAnnotation(annotation)) {
                qualifiers.add(annotation.name());
            }
        }
        
        // Add default qualifier if none found
        if (qualifiers.isEmpty()) {
            qualifiers.add(DotNames.DEFAULT);
        }
        
        return qualifiers;
    }

    /**
     * Creates a @Named qualifier with a specific value.
     */
    private DotName createQualifierWithNamedValue(ClassInfo beanClass, String value) {
        // This would create a custom qualifier annotation
        // Simplified for this example
        return DotNames.NAMED;
    }

    /**
     * Determines if an annotation is a qualifier.
     */
    private boolean isQualifierAnnotation(AnnotationInstance annotation) {
        // Check for common qualifier annotations
        return annotation.name().equals(DotNames.QUALIFIER) ||
               annotation.name().toString().endsWith("Qualifier");
    }

    /**
     * Determines the scope of a bean.
     */
    private DotName determineScope(ClassInfo beanClass) {
        // Check for scope annotations in order of precedence
        if (hasAnnotation(beanClass, DotNames.APPLICATION_SCOPED)) {
            return DotNames.APPLICATION_SCOPED;
        }
        if (hasAnnotation(beanClass, DotNames.NORMAL_SCOPED)) {
            return DotNames.NORMAL_SCOPED;
        }
        if (hasAnnotation(beanClass, DotNames.REQUEST_SCOPED)) {
            return DotNames.REQUEST_SCOPED;
        }
        if (hasAnnotation(beanClass, DotNames.SESSION_SCOPED)) {
            return DotNames.SESSION_SCOPED;
        }
        
        // Default to @Dependent if no scope found
        return DotNames.DEPENDENT;
    }

    /**
     * Processes a single bean and generates all necessary bytecode.
     * Delegates to {@link BeanGenerator} following the Quarkus ARC pattern.
     */
    private Collection<Resource> processBean(BeanInfo beanInfo) {
        Set<String> existingClasses = new HashSet<>();
        BeanGenerator beanGenerator = new BeanGenerator(false, reflectionRegistration,
                applicationClassPredicate::test, existingClasses);
        beanGenerator.precomputeGeneratedName(beanInfo);
        return beanGenerator.generate(beanInfo);
    }

    /**
     * Collects private members for reflection.
     */
    public static class PrivateMembersCollector {
        private final Set<FieldInfo> privateFields = new HashSet<>();
        private final Set<MethodInfo> privateMethods = new HashSet<>();
        private final Set<MethodInfo> privateConstructors = new HashSet<>();

        public void collect(ClassInfo beanClass) {
            // Collect private fields
            for (FieldInfo field : beanClass.fields()) {
                if (Modifier.isPrivate(field.flags())) {
                    privateFields.add(field);
                }
            }
            
            // Collect private methods
            for (MethodInfo method : beanClass.methods()) {
                if (Modifier.isPrivate(method.flags())) {
                    privateMethods.add(method);
                }
            }
            
            // Collect private constructors
            for (MethodInfo constructor : beanClass.constructors()) {
                if (Modifier.isPrivate(constructor.flags())) {
                    privateConstructors.add(constructor);
                }
            }
        }

        public Set<FieldInfo> getPrivateFields() {
            return Collections.unmodifiableSet(privateFields);
        }

        public Set<MethodInfo> getPrivateMethods() {
            return Collections.unmodifiableSet(privateMethods);
        }

        public Set<MethodInfo> getPrivateConstructors() {
            return Collections.unmodifiableSet(privateConstructors);
        }
    }
}
