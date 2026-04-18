package com.tanwir.miniquarkus.processor;

import static org.jboss.jandex.gizmo2.Jandex2Gizmo.classDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.methodDescOf;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;


import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.impl.BuiltinBean;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.creator.ClassCreator;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * BeanGenerator following Quarkus ARC patterns.
 * Generates bean classes that implement InjectableBean and follow the exact structure
 * used in Quarkus's ARC container.
 */
public class BeanGenerator extends AbstractGenerator {

    static final String BEAN_SUFFIX = "_Bean";
    static final String PRODUCER_METHOD_SUFFIX = "_ProducerMethod";
    static final String PRODUCER_FIELD_SUFFIX = "_ProducerField";

    protected static final String FIELD_NAME_DECLARING_PROVIDER_SUPPLIER = "declaringProviderSupplier";
    protected static final String FIELD_NAME_BEAN_TYPES = "types";
    protected static final String FIELD_NAME_QUALIFIERS = "qualifiers";
    protected static final String FIELD_NAME_STEREOTYPES = "stereotypes";
    protected static final String FIELD_NAME_PROXY = "proxy";

    private final Predicate<DotName> applicationClassPredicate;
    private final Set<String> existingClasses;
    private final Map<BeanInfo, String> beanToGeneratedName;
    private FieldDesc scopeField;

    public BeanGenerator(boolean generateSources, ReflectionRegistration reflectionRegistration,
                        Predicate<DotName> applicationClassPredicate, Set<String> existingClasses) {
        super(generateSources, reflectionRegistration);
        this.applicationClassPredicate = applicationClassPredicate;
        this.existingClasses = existingClasses;
        this.beanToGeneratedName = new HashMap<>();
    }

    /**
     * Precompute the generated name for the given bean.
     */
    public void precomputeGeneratedName(BeanInfo bean) {
        if (bean.isClassBean()) {
            generateClassBeanName(bean);
        } else if (bean.isProducer()) {
            generateProducerBeanName(bean);
        } else if (bean.isSynthetic()) {
            generateSyntheticBeanName(bean);
        }
    }

    private void generateClassBeanName(BeanInfo bean) {
        ClassInfo beanClass = getClassFromType(bean.getProviderType());
        String baseName = beanClass.name().withoutPackagePrefix();
        String targetPackage = packagePrefix(bean.getProviderType().name());
        String generatedName = generatedNameFromTarget(targetPackage, baseName, BEAN_SUFFIX);
        beanToGeneratedName.put(bean, generatedName);
    }

    private void generateProducerBeanName(BeanInfo bean) {
        // Simplified for example
        String baseName = "Producer";
        String targetPackage = packagePrefix(bean.getProviderType().name());
        String generatedName = generatedNameFromTarget(targetPackage, baseName, BEAN_SUFFIX);
        beanToGeneratedName.put(bean, generatedName);
    }

    private void generateSyntheticBeanName(BeanInfo bean) {
        String baseName = bean.getProviderType().name().withoutPackagePrefix() + UNDERSCORE + bean.getIdentifier()
                + UNDERSCORE + SYNTHETIC_SUFFIX;
        String targetPackage = packagePrefix(bean.getProviderType().name());
        String generatedName = generatedNameFromTarget(targetPackage, baseName, BEAN_SUFFIX);
        beanToGeneratedName.put(bean, generatedName);
    }

    /**
     * Generates the bean class.
     */
    public Collection<Resource> generate(BeanInfo bean) {
        String generatedName = beanToGeneratedName.get(bean);
        if (generatedName == null || existingClasses.contains(generatedName)) {
            return Collections.emptyList();
        }

        boolean isApplicationClass = applicationClassPredicate.test(bean.getBeanClass());
        ResourceClassOutput classOutput = createResourceClassOutput(isApplicationClass,
                name -> name.equals(generatedName) ? SpecialType.BEAN : null);

        Gizmo gizmo = gizmo(classOutput);

        generateBean(gizmo, bean, generatedName);

        return classOutput.getResources();
    }

    private void generateBean(Gizmo gizmo, BeanInfo bean, String generatedName) {
        gizmo.class_(generatedName, cc -> {
            cc.implements_(classDescOf(InjectableBean.class));

            // Generate fields following Quarkus patterns
            FieldDesc beanTypesField = generateBeanTypesField(cc, bean);
            FieldDesc qualifiersField = generateQualifiersField(cc, bean);
            FieldDesc stereotypesField = generateStereotypesField(cc, bean);
            FieldDesc declaringProviderSupplierField = generateDeclaringProviderSupplierField(cc, bean);
            scopeField = generateScopeField(cc, bean);

            // Generate constructor
            generateConstructor(cc, bean, beanTypesField, qualifiersField, stereotypesField, 
                              declaringProviderSupplierField);

            // Generate methods
            generateCreateMethod(cc, bean);
            generateGetTypesMethod(cc, beanTypesField);
            generateGetQualifiersMethod(cc, qualifiersField);
            generateGetScopeMethod(cc, bean);
            generateGetBeanClassMethod(cc, bean);
            generateGetImplementationClassMethod(cc, bean);
            generateGetNameMethod(cc, bean);
            generateGetKindMethod(cc, bean);
            generateEqualsMethod(cc, bean);
            generateHashCodeMethod(cc, bean);
            generateToStringMethod(cc);
        });
    }

    private FieldDesc generateBeanTypesField(ClassCreator cc, BeanInfo bean) {
        return cc.field(FIELD_NAME_BEAN_TYPES, fc -> {
            fc.private_();
            fc.final_();
            fc.setType(Set.class);
        });
    }

    private FieldDesc generateQualifiersField(ClassCreator cc, BeanInfo bean) {
        if (!bean.getQualifiers().isEmpty() && !bean.hasDefaultQualifiers()) {
            return cc.field(FIELD_NAME_QUALIFIERS, fc -> {
                fc.private_();
                fc.final_();
                fc.setType(Set.class);
            });
        }
        return null;
    }

    private FieldDesc generateStereotypesField(ClassCreator cc, BeanInfo bean) {
        // Simplified - always null for this example
        return null;
    }

    private FieldDesc generateDeclaringProviderSupplierField(ClassCreator cc, BeanInfo bean) {
        if (bean.isProducer()) {
            return cc.field(FIELD_NAME_DECLARING_PROVIDER_SUPPLIER, fc -> {
                fc.private_();
                fc.final_();
                fc.setType(ClassDesc.of("java.util.function.Supplier"));
            });
        }
        return null;
    }

    private FieldDesc generateScopeField(ClassCreator cc, BeanInfo bean) {
        return cc.field("scope", fc -> {
            fc.private_();
            fc.final_();
            fc.setType(ClassDesc.of("java.lang.Class"));
        });
    }

    private void generateConstructor(ClassCreator cc, BeanInfo bean, FieldDesc beanTypesField,
                                   FieldDesc qualifiersField, FieldDesc stereotypesField,
                                   FieldDesc declaringProviderSupplierField) {

        cc.constructor(mc -> {
            List<ParamVar> params = new ArrayList<>();
            
            // Add parameters
            params.add(mc.parameter("types", ClassDesc.of("java.util.Set")));
            
            if (qualifiersField != null) {
                params.add(mc.parameter("qualifiers", ClassDesc.of("java.util.Set")));
            }
            
            params.add(mc.parameter("scope", ClassDesc.of("java.lang.Class")));
            
            if (declaringProviderSupplierField != null) {
                params.add(mc.parameter("declaringProviderSupplier", ClassDesc.of("java.util.function.Supplier")));
            }

            mc.body(bc -> {
                // Always call super() first
                bc.invokeSpecial(ConstructorDesc.of(Object.class), cc.this_());

                // Set fields
                bc.set(cc.this_().field(beanTypesField), params.get(0));
                
                int paramIndex = 1;
                if (qualifiersField != null) {
                    bc.set(cc.this_().field(qualifiersField), params.get(paramIndex++));
                }
                bc.set(cc.this_().field(scopeField), params.get(paramIndex++));
                
                if (declaringProviderSupplierField != null) {
                    bc.set(cc.this_().field(declaringProviderSupplierField), params.get(paramIndex));
                }

                bc.return_();
            });
        });
    }

    private void generateCreateMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("create", mc -> {
            mc.public_();
            mc.returning(classDescOf(bean.getProviderType()));
            mc.parameter("creationalContext",
                ClassDesc.of("jakarta.enterprise.context.spi.CreationalContext"));

            mc.body(bc -> {
                bc.try_(tc -> {
                    tc.body(tbc -> {
                        if (bean.isClassBean()) {
                            // Direct constructor invocation — no reflection, following Quarkus ARC pattern
                            Expr instance = tbc.new_(ConstructorDesc.of(classDescOf(bean.getProviderType())));
                            tbc.return_(tbc.checkCast(instance, classDescOf(bean.getProviderType())));
                        } else {
                            tbc.throw_(tbc.new_(ConstructorDesc.of("java.lang.UnsupportedOperationException")));
                        }
                    });
                    tc.catch_(Exception.class, "e", (tbc, exceptionVar) -> {
                        tbc.throw_(tbc.new_(
                            ConstructorDesc.of("jakarta.enterprise.inject.CreationException", Throwable.class),
                            exceptionVar));
                    });
                });
            });
        });

        // Generate bridge method if needed
        if (!classDescOf(bean.getProviderType()).equals(ClassDesc.of(Object.class))) {
            cc.method("create", mc -> {
                mc.addFlag(io.quarkus.gizmo2.creator.ModifierFlag.BRIDGE);
                mc.returning(Object.class);
                ParamVar creationalContextParam = mc.parameter("creationalContext", 
                    ClassDesc.of("jakarta.enterprise.context.spi.CreationalContext"));
                mc.body(bc -> {
                    bc.return_(bc.invokeVirtual(
                        MethodDesc.of(cc.type(), "create", classDescOf(bean.getProviderType()), 
                            ClassDesc.of("jakarta.enterprise.context.spi.CreationalContext")),
                        cc.this_(), creationalContextParam));
                });
            });
        }
    }

    private void generateGetTypesMethod(ClassCreator cc, FieldDesc beanTypesField) {
        cc.method("getTypes", mc -> {
            mc.public_();
            mc.returning(ClassDesc.of("java.util.Set"));
            mc.body(bc -> {
                bc.return_(cc.this_().field(beanTypesField));
            });
        });
    }

    private void generateGetQualifiersMethod(ClassCreator cc, FieldDesc qualifiersField) {
        cc.method("getQualifiers", mc -> {
            mc.public_();
            mc.returning(ClassDesc.of("java.util.Set"));
            mc.body(bc -> {
                if (qualifiersField != null) {
                    bc.return_(cc.this_().field(qualifiersField));
                } else {
                    // Return immutable empty set for beans with only default qualifiers
                    bc.return_(bc.invokeStatic(
                        MethodDesc.of("java.util.Set", "of", ClassDesc.of("java.util.Set"))));
                }
            });
        });
    }

    private void generateGetScopeMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("getScope", mc -> {
            mc.public_();
            mc.returning(ClassDesc.of("java.lang.Class"));
            mc.body(bc -> {
                bc.return_(cc.this_().field(scopeField));
            });
        });
    }

    private void generateGetBeanClassMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("getBeanClass", mc -> {
            mc.public_();
            mc.returning(ClassDesc.of("java.lang.Class"));
            mc.body(bc -> {
                bc.return_(Const.of(classDescOf(bean.getBeanClass())));
            });
        });
    }

    private void generateGetImplementationClassMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("getImplementationClass", mc -> {
            mc.public_();
            mc.returning(ClassDesc.of("java.lang.Class"));
            mc.body(bc -> {
                if (bean.isProducer() || bean.isSynthetic()) {
                    bc.return_(Const.of(classDescOf(bean.getProviderType())));
                } else {
                    bc.return_(Const.of(classDescOf(bean.getBeanClass())));
                }
            });
        });
    }

    private void generateGetNameMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("getName", mc -> {
            mc.public_();
            mc.returning(String.class);
            mc.body(bc -> {
                bc.return_(Const.of(bean.getIdentifier()));
            });
        });
    }

    private void generateGetKindMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("getKind", mc -> {
            mc.public_();
            mc.returning(ClassDesc.of("jakarta.enterprise.inject.spi.BeanKind"));
            mc.body(bc -> {
                if (bean.isClassBean()) {
                    bc.return_(bc.staticField(FieldDesc.of("jakarta.enterprise.inject.spi.BeanKind", "CLASS")));
                } else if (bean.isProducer()) {
                    bc.return_(bc.staticField(FieldDesc.of("jakarta.enterprise.inject.spi.BeanKind", "PRODUCER")));
                } else {
                    bc.return_(bc.staticField(FieldDesc.of("jakarta.enterprise.inject.spi.BeanKind", "INTERCEPTOR")));
                }
            });
        });
    }

    private void generateEqualsMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("equals", mc -> {
            mc.public_();
            mc.returning(boolean.class);
            ParamVar otherParam = mc.parameter("other", Object.class);
            mc.body(bc -> {
                // if (this == other) return true;
                bc.if_(bc.sameAs(cc.this_(), otherParam), thenBlock -> {
                    thenBlock.return_(Const.of(true));
                });

                // if (other == null) return false;
                bc.if_(bc.isNull(otherParam), thenBlock -> {
                    thenBlock.return_(Const.of(false));
                });

                // if (!(other instanceof ThisClass)) return false;
                bc.if_(bc.not(bc.instanceOf(otherParam, cc.type())), thenBlock -> {
                    thenBlock.return_(Const.of(false));
                });

                // return identifier.equals(((ThisClass) other).getName());
                LocalVar otherBean = bc.localVar("otherBean", bc.checkCast(otherParam, cc.type()));
                Expr result = bc.invokeVirtual(
                    MethodDesc.of(Object.class, "equals", boolean.class, Object.class),
                    Const.of(bean.getIdentifier()),
                    bc.invokeVirtual(MethodDesc.of(cc.type(), "getName", String.class), otherBean));

                bc.return_(result);
            });
        });
    }

    private void generateHashCodeMethod(ClassCreator cc, BeanInfo bean) {
        cc.method("hashCode", mc -> {
            mc.public_();
            mc.returning(int.class);
            mc.body(bc -> {
                bc.return_(bc.invokeVirtual(
                    MethodDesc.of(Object.class, "hashCode", int.class),
                    Const.of(bean.getIdentifier())));
            });
        });
    }

    private void generateToStringMethod(ClassCreator cc) {
        cc.method("toString", mc -> {
            mc.public_();
            mc.returning(String.class);
            mc.body(bc -> {
                Expr className = bc.invokeVirtual(MethodDesc.of(Object.class, "getClass", Class.class), cc.this_());
                Expr simpleName = bc.invokeVirtual(MethodDesc.of(Class.class, "getSimpleName", String.class), className);
                bc.return_(simpleName);
            });
        });
    }

    // Helper methods
    private ClassInfo getClassFromType(Type type) {
        // Simplified - in real implementation this would use Jandex index
        return null;
    }

    private String packagePrefix(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

}
