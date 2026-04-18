package com.tanwir.miniquarkus.processor;

import java.util.List;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

/**
 * BeanInfo implementation following Quarkus ARC patterns.
 * Represents metadata about a bean for bytecode generation.
 */
public class BeanInfo {

    private final DotName beanClass;
    private final Type providerType;
    private final Set<Type> types;
    private final Set<DotName> qualifiers;
    private final DotName scope;
    private final DeploymentInfo deployment;
    private final String identifier;

    public BeanInfo(DotName beanClass, Type providerType, Set<Type> types, Set<DotName> qualifiers, 
                   DotName scope, DeploymentInfo deployment, String identifier) {
        this.beanClass = beanClass;
        this.providerType = providerType;
        this.types = types;
        this.qualifiers = qualifiers;
        this.scope = scope;
        this.deployment = deployment;
        this.identifier = identifier;
    }

    public DotName getBeanClass() {
        return beanClass;
    }

    public Type getProviderType() {
        return providerType;
    }

    public Set<Type> getTypes() {
        return types;
    }

    public Set<DotName> getQualifiers() {
        return qualifiers;
    }

    public DotName getScope() {
        return scope;
    }

    public DeploymentInfo getDeployment() {
        return deployment;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isClassBean() {
        return true; // Simplified for example
    }

    public boolean isProducer() {
        return false; // Simplified for example
    }

    public boolean isSynthetic() {
        return false; // Simplified for example
    }

    public boolean hasDestroyLogic() {
        return false; // Simplified for example
    }

    public boolean hasDefaultQualifiers() {
        return qualifiers.isEmpty();
    }

    public boolean isDefaultBean() {
        return false; // Simplified for example
    }

    /**
     * Deployment information following Quarkus patterns.
     */
    public static class DeploymentInfo {
        private final boolean transformPrivateInjectedFields;
        private final ClassInfo beanArchiveIndex;

        public DeploymentInfo(boolean transformPrivateInjectedFields, ClassInfo beanArchiveIndex) {
            this.transformPrivateInjectedFields = transformPrivateInjectedFields;
            this.beanArchiveIndex = beanArchiveIndex;
        }

        public boolean isTransformPrivateInjectedFields() {
            return transformPrivateInjectedFields;
        }

        public ClassInfo getBeanArchiveIndex() {
            return beanArchiveIndex;
        }
    }
}
