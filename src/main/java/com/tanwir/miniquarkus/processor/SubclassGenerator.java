package com.tanwir.miniquarkus.processor;

import static org.jboss.jandex.gizmo2.Jandex2Gizmo.classDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.methodDescOf;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;


import io.quarkus.arc.Subclass;
import io.quarkus.arc.impl.InterceptedMethodMetadata;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.Var;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.creator.ClassCreator;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * SubclassGenerator following Quarkus ARC patterns.
 * Generates subclasses for intercepted and decorated beans.
 */
public class SubclassGenerator extends AbstractGenerator {

    static final String SUBCLASS_SUFFIX = "_Subclass";
    static final String MARK_CONSTRUCTED_METHOD_NAME = "arc$markConstructed";
    static final String DESTROY_METHOD_NAME = "arc$destroy";

    protected static final String FIELD_NAME_PREDESTROYS = "arc$preDestroys";
    protected static final String FIELD_NAME_CONSTRUCTED = "arc$constructed";

    private final Predicate<DotName> applicationClassPredicate;
    private final Set<String> existingClasses;

    public SubclassGenerator(boolean generateSources, ReflectionRegistration reflectionRegistration,
                            Predicate<DotName> applicationClassPredicate, Set<String> existingClasses) {
        super(generateSources, reflectionRegistration);
        this.applicationClassPredicate = applicationClassPredicate;
        this.existingClasses = existingClasses;
    }

    /**
     * Generates a subclass for the given bean.
     */
    public Collection<Resource> generate(BeanInfo bean, String beanClassName) {
        Type providerType = bean.getProviderType();
        String baseName = getBeanBaseName(beanClassName);
        String generatedName = generatedNameFromTarget(packagePrefix(providerType.name()), baseName, SUBCLASS_SUFFIX);
        
        if (existingClasses.contains(generatedName)) {
            return Collections.emptyList();
        }

        boolean isApplicationClass = applicationClassPredicate.test(bean.getBeanClass());
        ResourceClassOutput classOutput = createResourceClassOutput(isApplicationClass,
                name -> name.equals(generatedName) ? SpecialType.SUBCLASS : null);

        Gizmo gizmo = gizmo(classOutput);

        createSubclass(gizmo, bean, generatedName, providerType);

        return classOutput.getResources();
    }

    private void createSubclass(Gizmo gizmo, BeanInfo bean, String generatedName, Type providerType) {
        // Foo_Subclass extends Foo implements Subclass
        gizmo.class_(generatedName, cc -> {
            cc.extends_(classDescOf(providerType));
            cc.implements_(classDescOf(Subclass.class));

            // Generate fields
            FieldDesc constructedField = generateConstructedField(cc);
            FieldDesc preDestroysField = generatePreDestroysField(cc, bean);
            Map<MethodInfo, FieldDesc> metadataFields = generateMetadataFields(cc, bean);

            // Generate constructor
            generateConstructor(cc, bean, constructedField, preDestroysField);

            // Generate lifecycle methods
            generateMarkConstructedMethod(cc, constructedField);
            generateDestroyMethod(cc, bean, preDestroysField);

            // Generate intercepted methods
            generateInterceptedMethods(cc, bean, metadataFields, constructedField);
        });
    }

    private FieldDesc generateConstructedField(ClassCreator cc) {
        return cc.field(FIELD_NAME_CONSTRUCTED, fc -> {
            fc.private_();
            fc.volatile_();
            fc.setType(boolean.class);
        });
    }

    private FieldDesc generatePreDestroysField(ClassCreator cc, BeanInfo bean) {
        // Check if bean has pre-destroy interceptors (simplified)
        if (hasPreDestroyInterceptors(bean)) {
            return cc.field(FIELD_NAME_PREDESTROYS, fc -> {
                fc.private_();
                fc.final_();
                fc.setType(classDescOf("java.util.List"));
            });
        }
        return null;
    }

    private Map<MethodInfo, FieldDesc> generateMetadataFields(ClassCreator cc, BeanInfo bean) {
        Map<MethodInfo, FieldDesc> metadataFields = new HashMap<>();
        
        // For each intercepted method, generate a metadata field
        for (MethodInfo method : getInterceptedMethods(bean)) {
            FieldDesc metadataField = cc.field("arc$" + method.name(), fc -> {
                fc.private_();
                fc.setType(classDescOf(InterceptedMethodMetadata.class));
            });
            metadataFields.put(method, metadataField);
        }
        
        return metadataFields;
    }

    private void generateConstructor(ClassCreator cc, BeanInfo bean, FieldDesc constructedField, FieldDesc preDestroysField) {
        cc.constructor(mc -> {
            List<ParamVar> params = new ArrayList<>();
            
            // Add constructor injection parameters (simplified)
            params.add(mc.parameter("creationalContext", classDescOf("jakarta.enterprise.context.spi.CreationalContext")));
            
            // Add interceptor parameters
            for (int i = 0; i < getInterceptorCount(bean); i++) {
                params.add(mc.parameter("interceptor" + i, classDescOf("io.quarkus.arc.InjectableInterceptor")));
            }

            mc.body(bc -> {
                // Invoke super constructor
                bc.invokeSpecial(ConstructorDesc.of(classDescOf(bean.getProviderType())), cc.this_());

                // Initialize pre-destroy interceptors if needed
                if (preDestroysField != null) {
                    LocalVar preDestroysList = bc.localVar("preDestroys", bc.new_(classDescOf("java.util.ArrayList")));
                    
                    // Add pre-destroy interceptors to list
                    for (int i = 0; i < getPreDestroyInterceptorCount(bean); i++) {
                        Expr interceptor = params.get(1 + i); // Skip creationalContext
                        Expr invocation = bc.invokeStatic(
                            MethodDesc.of("io.quarkus.arc.impl.InvocationContextImpl$InterceptorInvocation", "preDestroy", 
                                classDescOf("io.quarkus.arc.impl.InvocationContextImpl$InterceptorInvocation"),
                                classDescOf("io.quarkus.arc.InjectableInterceptor"), classDescOf("io.quarkus.arc.InjectableInterceptor")),
                            interceptor, interceptor);
                        bc.withList(preDestroysList).add(invocation);
                    }
                    
                    bc.set(cc.this_().field(preDestroysField), preDestroysList);
                }

                // Initialize constructed field
                bc.set(cc.this_().field(constructedField), Const.of(false));

                bc.return_();
            });
        });
    }

    private void generateMarkConstructedMethod(ClassCreator cc, FieldDesc constructedField) {
        cc.method(MARK_CONSTRUCTED_METHOD_NAME, mc -> {
            mc.public_();
            mc.returning(void.class);
            mc.body(bc -> {
                bc.set(cc.this_().field(constructedField), Const.of(true));
                bc.return_();
            });
        });
    }

    private void generateDestroyMethod(ClassCreator cc, BeanInfo bean, FieldDesc preDestroysField) {
        if (preDestroysField != null) {
            cc.method(DESTROY_METHOD_NAME, mc -> {
                mc.public_();
                mc.returning(void.class);
                ParamVar forwardParam = mc.parameter("forward", classDescOf("java.lang.Runnable"));
                mc.body(bc -> {
                    bc.try_(tc -> {
                        tc.body(tbc -> {
                            // Create bindings set
                            Expr bindings = tbc.setOf(getPreDestroyBindings(bean));
                            
                            // Create invocation context
                            Expr invocationContext = tbc.invokeStatic(
                                MethodDesc.of("io.quarkus.arc.impl.InvocationContexts", "preDestroy",
                                    classDescOf("jakarta.interceptor.InvocationContext"),
                                    Object.class, classDescOf("java.util.List"), classDescOf("java.util.Set"), classDescOf("java.lang.Runnable")),
                                cc.this_(), cc.this_().field(preDestroysField), bindings, forwardParam);
                            
                            // Proceed with invocation
                            tbc.invokeInterface(
                                MethodDesc.of(classDescOf("jakarta.interceptor.InvocationContext"), "proceed", Object.class),
                                invocationContext);
                            
                            tbc.return_();
                        });
                        tc.catch_(Exception.class, "e", (tbc, exceptionVar) -> {
                            tbc.throw_(tbc.new_(ConstructorDesc.of("java.lang.RuntimeException", String.class, Throwable.class),
                                Const.of("Error destroying subclass"), exceptionVar));
                        });
                    });
                });
            });
        }
    }

    private void generateInterceptedMethods(ClassCreator cc, BeanInfo bean, 
                                          Map<MethodInfo, FieldDesc> metadataFields, FieldDesc constructedField) {
        
        for (Map.Entry<MethodInfo, FieldDesc> entry : metadataFields.entrySet()) {
            MethodInfo method = entry.getKey();
            FieldDesc metadataField = entry.getValue();
            
            generateInterceptedMethod(cc, bean, method, metadataField, constructedField);
        }
    }

    private void generateInterceptedMethod(ClassCreator cc, BeanInfo bean, MethodInfo method, 
                                         FieldDesc metadataField, FieldDesc constructedField) {
        
        MethodDesc methodDesc = methodDescOf(method);
        List<Type> parameterTypes = method.parameterTypes();
        
        cc.method(methodDesc, mc -> {
            mc.public_();
            
            List<ParamVar> params = new ArrayList<>();
            for (int i = 0; i < parameterTypes.size(); i++) {
                params.add(mc.parameter("param" + i, classDescOf(parameterTypes.get(i))));
            }
            
            mc.body(bc -> {
                // Check if constructed
                bc.if_(bc.not(cc.this_().field(constructedField)), thenBlock -> {
                    if (method.isAbstract()) {
                        thenBlock.throw_(thenBlock.new_(ConstructorDesc.of("java.lang.IllegalStateException", String.class),
                            Const.of("Cannot invoke abstract method before construction")));
                    } else {
                        // Call super method
                        thenBlock.return_(thenBlock.invokeSuper(methodDesc, cc.this_(), params.toArray(new ParamVar[0])));
                    }
                });
                
                // Get metadata
                Expr metadata = cc.this_().field(metadataField);
                
                // Create invocation context
                LocalVar context = bc.localVar("context", bc.invokeStatic(
                    MethodDesc.of("io.quarkus.arc.impl.InvocationContexts", "aroundInvoke",
                        classDescOf("jakarta.interceptor.InvocationContext"),
                        Object.class, classDescOf("io.quarkus.arc.impl.InterceptedMethodMetadata"), 
                        classDescOf("java.lang.Object[]"), classDescOf("java.util.function.BiFunction")),
                    cc.this_(), metadata, bc.newArray(Object.class, params.size()), 
                    createForwardingFunction(bc, method, params)));
                
                // Proceed with interception
                Expr result = bc.invokeInterface(
                    MethodDesc.of(classDescOf("jakarta.interceptor.InvocationContext"), "proceed", Object.class),
                    context);
                
                // Handle return type
                if (method.returnType().kind() == org.jboss.jandex.Type.Kind.VOID) {
                    bc.return_();
                } else {
                    bc.return_(bc.checkCast(result, classDescOf(method.returnType())));
                }
            });
        });
    }

    private Expr createForwardingFunction(BlockCreator bc, MethodInfo method, List<ParamVar> params) {
        return bc.lambda(classDescOf("java.util.function.BiFunction"), lambda -> {
            Var capturedParams = lambda.capture(params);
            ParamVar target = lambda.parameter("target", 0);
            ParamVar ctx = lambda.parameter("ctx", 1);
            
            lambda.body(lbc -> {
                // Extract parameters from context
                LocalVar contextParams = lbc.localVar("contextParams", 
                    lbc.invokeInterface(
                        MethodDesc.of(classDescOf("jakarta.interceptor.InvocationContext"), "getParameters", Object[].class),
                        ctx));
                
                // Call super method
                Expr[] superParams = new Expr[capturedParams.size()];
                for (int i = 0; i < capturedParams.size(); i++) {
                    superParams[i] = contextParams.elem(i);
                }
                
                Expr superResult = lbc.invokeSuper(methodDescOf(method), target, superParams);
                
                // Handle void methods
                if (method.returnType().kind() == org.jboss.jandex.Type.Kind.VOID) {
                    lbc.return_(Const.ofNull(Object.class));
                } else {
                    lbc.return_(superResult);
                }
            });
        });
    }

    // Helper methods (simplified for example)
    private boolean hasPreDestroyInterceptors(BeanInfo bean) {
        return false; // Simplified
    }

    private List<MethodInfo> getInterceptedMethods(BeanInfo bean) {
        return Collections.emptyList(); // Simplified
    }

    private int getInterceptorCount(BeanInfo bean) {
        return 0; // Simplified
    }

    private int getPreDestroyInterceptorCount(BeanInfo bean) {
        return 0; // Simplified
    }

    private List<AnnotationInstance> getPreDestroyBindings(BeanInfo bean) {
        return Collections.emptyList(); // Simplified
    }

    private String packagePrefix(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }
}
