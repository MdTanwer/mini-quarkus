package com.tanwir.miniquarkus.processor;

import static org.jboss.jandex.gizmo2.Jandex2Gizmo.classDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.methodDescOf;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.interceptor.InvocationContext;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import com.tanwir.miniquarkus.processor.BeanInfo;
import com.tanwir.miniquarkus.processor.DotNames;
import com.tanwir.miniquarkus.processor.MethodDescs;
import com.tanwir.miniquarkus.processor.ReflectionRegistration;
import com.tanwir.miniquarkus.processor.ResourceClassOutput;
import com.tanwir.miniquarkus.processor.ResourceOutput.Resource;
import com.tanwir.miniquarkus.processor.ResourceOutput.Resource.SpecialType;

import io.quarkus.arc.InjectableInterceptor;
import io.quarkus.arc.InterceptorCreator;
import io.quarkus.arc.InterceptorCreator.InterceptFunction;
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
 * InterceptorGenerator following Quarkus ARC patterns.
 * Generates bytecode for interceptor classes that implement InjectableInterceptor.
 */
public class InterceptorGenerator extends AbstractGenerator {

    private static final String INTERCEPTOR_SUFFIX = "_Interceptor";

    private final Predicate<DotName> applicationClassPredicate;
    private final Set<String> existingClasses;
    private final ReflectionRegistration reflectionRegistration;

    public InterceptorGenerator(boolean generateSources, ReflectionRegistration reflectionRegistration,
                              Predicate<DotName> applicationClassPredicate, Set<String> existingClasses) {
        super(generateSources, reflectionRegistration);
        this.applicationClassPredicate = applicationClassPredicate;
        this.existingClasses = existingClasses;
        this.reflectionRegistration = reflectionRegistration;
    }

    /**
     * Generates an interceptor class for the given interceptor info.
     */
    public Collection<Resource> generate(InterceptorInfo interceptorInfo) {
        String generatedName = generateInterceptorClassName(interceptorInfo);
        
        if (existingClasses.contains(generatedName)) {
            return Collections.emptyList();
        }

        boolean isApplicationClass = applicationClassPredicate.test(interceptorInfo.getInterceptorClass());
        ResourceClassOutput classOutput = createResourceClassOutput(isApplicationClass,
                name -> name.equals(generatedName) ? SpecialType.INTERCEPTOR : null);

        Gizmo gizmo = gizmo(classOutput);

        createInterceptorClass(gizmo, interceptorInfo, generatedName);

        return classOutput.getResources();
    }

    private String generateInterceptorClassName(InterceptorInfo interceptorInfo) {
        String baseName = interceptorInfo.getInterceptorClass().name().withoutPackagePrefix();
        String targetPackage = packagePrefix(interceptorInfo.getInterceptorClass().name());
        return generatedNameFromTarget(targetPackage, baseName, INTERCEPTOR_SUFFIX);
    }

    private void createInterceptorClass(Gizmo gizmo, InterceptorInfo interceptorInfo, String generatedName) {
        gizmo.class_(generatedName, cc -> {
            cc.implements_(classDescOf(InjectableInterceptor.class));

            // Add fields
            FieldDesc interceptorClassField = generateInterceptorClassField(cc, interceptorInfo);
            FieldDesc priorityField = generatePriorityField(cc, interceptorInfo);
            FieldDesc aroundInvokeField = generateAroundInvokeField(cc, interceptorInfo);
            FieldDesc postConstructField = generatePostConstructField(cc, interceptorInfo);
            FieldDesc preDestroyField = generatePreDestroyField(cc, interceptorInfo);

            // Generate constructor
            generateInterceptorConstructor(cc, interceptorInfo, interceptorClassField, priorityField, 
                                        aroundInvokeField, postConstructField, preDestroyField);

            // Generate methods
            generateGetInterceptorClassMethod(cc, interceptorClassField);
            generateGetPriorityMethod(cc, priorityField);
            generateGetAroundInvokeMethod(cc, aroundInvokeField);
            generateGetInterceptionTypesMethod(cc, interceptorInfo);
            generateInterceptMethod(cc, interceptorInfo);
        });
    }

    private FieldDesc generateInterceptorClassField(ClassCreator cc, InterceptorInfo interceptorInfo) {
        return cc.field("interceptorClass", fc -> {
            fc.private_();
            fc.final_();
            fc.setType(Class.class);
        });
    }

    private FieldDesc generatePriorityField(ClassCreator cc, InterceptorInfo interceptorInfo) {
        return cc.field("priority", fc -> {
            fc.private_();
            fc.final_();
            fc.setType(int.class);
        });
    }

    private FieldDesc generateAroundInvokeField(ClassCreator cc, InterceptorInfo interceptorInfo) {
        return cc.field("aroundInvoke", fc -> {
            fc.private_();
            fc.setType(BiFunction.class);
        });
    }

    private FieldDesc generatePostConstructField(ClassCreator cc, InterceptorInfo interceptorInfo) {
        return cc.field("postConstruct", fc -> {
            fc.private_();
            fc.setType(Consumer.class);
        });
    }

    private FieldDesc generatePreDestroyField(ClassCreator cc, InterceptorInfo interceptorInfo) {
        return cc.field("preDestroy", fc -> {
            fc.private_();
            fc.setType(Consumer.class);
        });
    }

    private void generateInterceptorConstructor(ClassCreator cc, InterceptorInfo interceptorInfo,
                                          FieldDesc interceptorClassField, FieldDesc priorityField,
                                          FieldDesc aroundInvokeField, FieldDesc postConstructField,
                                          FieldDesc preDestroyField) {
        cc.constructor(mc -> {
            ParamVar interceptorClassParam = mc.parameter("interceptorClass", Class.class);
            ParamVar priorityParam = mc.parameter("priority", int.class);
            ParamVar aroundInvokeParam = mc.parameter("aroundInvoke", BiFunction.class);
            ParamVar postConstructParam = mc.parameter("postConstruct", Consumer.class);
            ParamVar preDestroyParam = mc.parameter("preDestroy", Consumer.class);

            mc.body(bc -> {
                // Always call super() first
                bc.invokeSpecial(ConstructorDesc.of(Object.class), cc.this_());

                // Initialize fields
                bc.set(cc.this_().field(interceptorClassField), interceptorClassParam);
                bc.set(cc.this_().field(priorityField), priorityParam);
                bc.set(cc.this_().field(aroundInvokeField), aroundInvokeParam);
                bc.set(cc.this_().field(postConstructField), postConstructParam);
                bc.set(cc.this_().field(preDestroyField), preDestroyParam);

                bc.return_();
            });
        });
    }

    private void generateGetInterceptorClassMethod(ClassCreator cc, FieldDesc interceptorClassField) {
        cc.method("getInterceptorClass", mc -> {
            mc.public_();
            mc.returning(Class.class);
            mc.body(bc -> {
                bc.return_(cc.this_().field(interceptorClassField));
            });
        });
    }

    private void generateGetPriorityMethod(ClassCreator cc, FieldDesc priorityField) {
        cc.method("getPriority", mc -> {
            mc.public_();
            mc.returning(int.class);
            mc.body(bc -> {
                bc.return_(cc.this_().field(priorityField));
            });
        });
    }

    private void generateGetAroundInvokeMethod(ClassCreator cc, FieldDesc aroundInvokeField) {
        cc.method("getAroundInvoke", mc -> {
            mc.public_();
            mc.returning(BiFunction.class);
            mc.body(bc -> {
                bc.return_(cc.this_().field(aroundInvokeField));
            });
        });
    }

    private void generateGetInterceptionTypesMethod(ClassCreator cc, InterceptorInfo interceptorInfo) {
        cc.method("getInterceptionTypes", mc -> {
            mc.public_();
            mc.returning(Set.class);
            mc.body(bc -> {
                // Create set of interception types
                LocalVar types = bc.localVar("types", bc.new_(HashSet.class));
                
                for (InterceptionType type : interceptorInfo.getInterceptionTypes()) {
                    bc.withList(types).add(bc.staticField(DotNames.INTERCEPTION_TYPE));
                }
                
                bc.return_(types);
            });
        });
    }

    private void generateInterceptMethod(ClassCreator cc, InterceptorInfo interceptorInfo) {
        cc.method("intercept", mc -> {
            mc.public_();
            mc.returning(Object.class);
            ParamVar contextParam = mc.parameter("context", InvocationContext.class);
            
            mc.body(bc -> {
                // Get interception type
                LocalVar interceptionType = bc.localVar("interceptionType", 
                    bc.invokeInterface(MethodDescs.INVOCATION_CONTEXT_GET_METHOD, contextParam));
                
                // Handle different interception types
                bc.if_(bc.enumValue(interceptionType, DotNames.INTERCEPTION_TYPE_AROUND_INVOKE), thenBlock -> {
                    // Around invoke
                    Expr aroundInvoke = cc.this_().field(aroundInvokeField);
                    Expr result = bc.invokeInterface(BiFunction.class, aroundInvoke, 
                        bc.invokeInterface(MethodDescs.INVOCATION_CONTEXT_GET_TARGET, contextParam), contextParam);
                    thenBlock.return_(result);
                });
                
                bc.if_(bc.enumValue(interceptionType, DotNames.INTERCEPTION_TYPE_POST_CONSTRUCT), thenBlock -> {
                    // Post construct
                    Expr postConstruct = cc.this_().field(postConstructField);
                    bc.invokeInterface(Consumer.class, postConstruct, 
                        bc.invokeInterface(MethodDescs.INVOCATION_CONTEXT_GET_TARGET, contextParam));
                    thenBlock.return_(Const.ofNull(Object.class));
                });
                
                bc.if_(bc.enumValue(interceptionType, DotNames.INTERCEPTION_TYPE_PRE_DESTROY), thenBlock -> {
                    // Pre destroy
                    Expr preDestroy = cc.this_().field(preDestroyField);
                    bc.invokeInterface(Consumer.class, preDestroy, 
                        bc.invokeInterface(MethodDescs.INVOCATION_CONTEXT_GET_TARGET, contextParam));
                    thenBlock.return_(Const.ofNull(Object.class));
                });
                
                // Default case
                bc.return_(Const.ofNull(Object.class));
            });
        });
    }

    private String packagePrefix(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * Information about an interceptor to be generated.
     */
    public static class InterceptorInfo {
        private final ClassInfo interceptorClass;
        private final Set<InterceptionType> interceptionTypes;
        private final int priority;

        public InterceptorInfo(ClassInfo interceptorClass, Set<InterceptionType> interceptionTypes, int priority) {
            this.interceptorClass = interceptorClass;
            this.interceptionTypes = interceptionTypes;
            this.priority = priority;
        }

        public ClassInfo getInterceptorClass() {
            return interceptorClass;
        }

        public Set<InterceptionType> getInterceptionTypes() {
            return interceptionTypes;
        }

        public int getPriority() {
            return priority;
        }
    }
}
