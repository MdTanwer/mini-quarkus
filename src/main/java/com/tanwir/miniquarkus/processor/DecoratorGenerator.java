package com.tanwir.miniquarkus.processor;

import static org.jboss.jandex.gizmo2.Jandex2Gizmo.classDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.constructorDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.methodDescOf;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.enterprise.context.spi.CreationalContext;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import com.tanwir.miniquarkus.processor.BeanInfo;
import com.tanwir.miniquarkus.processor.DotNames;
import com.tanwir.miniquarkus.processor.MethodDescs;
import com.tanwir.miniquarkus.processor.ReflectionRegistration;
import com.tanwir.miniquarkus.processor.ResourceClassOutput;
import com.tanwir.miniquarkus.processor.ResourceOutput.Resource;
import com.tanwir.miniquarkus.processor.ResourceOutput.Resource.SpecialType;

import io.quarkus.arc.InjectableDecorator;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.creator.ClassCreator;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * DecoratorGenerator following Quarkus ARC patterns.
 * Generates bytecode for decorator classes that implement InjectableDecorator.
 */
public class DecoratorGenerator extends AbstractGenerator {

    protected static final String FIELD_NAME_DECORATED_TYPES = "decoratedTypes";
    protected static final String FIELD_NAME_DELEGATE_TYPE = "delegateType";

    private final Predicate<DotName> applicationClassPredicate;
    private final Set<String> existingClasses;

    public DecoratorGenerator(boolean generateSources, ReflectionRegistration reflectionRegistration,
                               Predicate<DotName> applicationClassPredicate, Set<String> existingClasses) {
        super(generateSources, reflectionRegistration);
        this.applicationClassPredicate = applicationClassPredicate;
        this.existingClasses = existingClasses;
    }

    /**
     * Generates a decorator class for the given decorator info.
     */
    public Collection<Resource> generate(DecoratorInfo decoratorInfo) {
        String generatedName = generateDecoratorClassName(decoratorInfo);
        
        if (existingClasses.contains(generatedName)) {
            return Collections.emptyList();
        }

        boolean isApplicationClass = applicationClassPredicate.test(decoratorInfo.getDecoratorClass());
        ResourceClassOutput classOutput = createResourceClassOutput(isApplicationClass,
                name -> name.equals(generatedName) ? SpecialType.DECORATOR : null);

        Gizmo gizmo = gizmo(classOutput);

        createDecoratorClass(gizmo, decoratorInfo, generatedName);

        return classOutput.getResources();
    }

    private String generateDecoratorClassName(DecoratorInfo decoratorInfo) {
        String baseName = decoratorInfo.getDecoratorClass().name().withoutPackagePrefix();
        String targetPackage = packagePrefix(decoratorInfo.getDecoratorClass().name());
        return generatedNameFromTarget(targetPackage, baseName, "_Decorator");
    }

    private void createDecoratorClass(Gizmo gizmo, DecoratorInfo decoratorInfo, String generatedName) {
        gizmo.class_(generatedName, cc -> {
            cc.implements_(classDescOf(InjectableDecorator.class));

            // Add fields
            FieldDesc decoratedTypesField = generateDecoratedTypesField(cc, decoratorInfo);
            FieldDesc delegateTypeField = generateDelegateTypeField(cc, decoratorInfo);
            FieldDesc delegateField = generateDelegateField(cc, decoratorInfo);

            // Generate constructor
            generateDecoratorConstructor(cc, decoratorInfo, decoratedTypesField, delegateTypeField, delegateField);

            // Generate methods
            generateGetDecoratedTypesMethod(cc, decoratedTypesField);
            generateGetDelegateTypeMethod(cc, delegateTypeField);
            generateGetDelegateMethod(cc, delegateField);
            generateDecoratesMethod(cc, decoratorInfo);
            generateGetDelegateClassMethod(cc, decoratorInfo);
            generateGetPriorityMethod(cc, decoratorInfo);
        });
    }

    private FieldDesc generateDecoratedTypesField(ClassCreator cc, DecoratorInfo decoratorInfo) {
        return cc.field(FIELD_NAME_DECORATED_TYPES, fc -> {
            fc.private_();
            fc.final_();
            fc.setType(Set.class);
        });
    }

    private FieldDesc generateDelegateTypeField(ClassCreator cc, DecoratorInfo decoratorInfo) {
        return cc.field(FIELD_NAME_DELEGATE_TYPE, fc -> {
            fc.private_();
            fc.final_();
            fc.setType(Class.class);
        });
    }

    private FieldDesc generateDelegateField(ClassCreator cc, DecoratorInfo decoratorInfo) {
        return cc.field("delegate", fc -> {
            fc.private_();
            fc.final_();
            fc.setType(Supplier.class);
        });
    }

    private void generateDecoratorConstructor(ClassCreator cc, DecoratorInfo decoratorInfo,
                                       FieldDesc decoratedTypesField, FieldDesc delegateTypeField,
                                       FieldDesc delegateField) {
        cc.constructor(mc -> {
            ParamVar decoratedTypesParam = mc.parameter("decoratedTypes", ClassDesc.of(Set.class));
            ParamVar delegateTypeParam = mc.parameter("delegateType", Class.class);
            ParamVar delegateParam = mc.parameter("delegate", Supplier.class);

            mc.body(bc -> {
                // Always call super() first
                bc.invokeSpecial(ConstructorDesc.of(Object.class), cc.this_());

                // Initialize fields
                bc.set(cc.this_().field(decoratedTypesField), decoratedTypesParam);
                bc.set(cc.this_().field(delegateTypeField), delegateTypeParam);
                bc.set(cc.this_().field(delegateField), delegateParam);

                bc.return_();
            });
        });
    }

    private void generateGetDecoratedTypesMethod(ClassCreator cc, FieldDesc decoratedTypesField) {
        cc.method("getDecoratedTypes", mc -> {
            mc.public_();
            mc.returning(Set.class);
            mc.body(bc -> {
                bc.return_(cc.this_().field(decoratedTypesField));
            });
        });
    }

    private void generateGetDelegateTypeMethod(ClassCreator cc, FieldDesc delegateTypeField) {
        cc.method("getDelegateType", mc -> {
            mc.public_();
            mc.returning(Class.class);
            mc.body(bc -> {
                bc.return_(cc.this_().field(delegateTypeField));
            });
        });
    }

    private void generateGetDelegateMethod(ClassCreator cc, FieldDesc delegateField) {
        cc.method("getDelegate", mc -> {
            mc.public_();
            mc.returning(Supplier.class);
            mc.body(bc -> {
                bc.return_(cc.this_().field(delegateField));
            });
        });
    }

    private void generateDecoratesMethod(ClassCreator cc, DecoratorInfo decoratorInfo) {
        cc.method("decorates", mc -> {
            mc.public_();
            mc.returning(Set.class);
            mc.body(bc -> {
                // Return set of decorated types
                LocalVar decoratedTypes = bc.localVar("decoratedTypes", bc.new_(HashSet.class));
                for (Type decoratedType : decoratorInfo.getDecoratedTypes()) {
                    bc.withList(decoratedTypes).add(Const.of(classDescOf(decoratedType)));
                }
                bc.return_(decoratedTypes);
            });
        });
    }

    private void generateGetDelegateClassMethod(ClassCreator cc, DecoratorInfo decoratorInfo) {
        cc.method("getDelegateClass", mc -> {
            mc.public_();
            mc.returning(Class.class);
            mc.body(bc -> {
                bc.return_(Const.of(classDescOf(decoratorInfo.getDelegateClass())));
            });
        });
    }

    private void generateGetPriorityMethod(ClassCreator cc, DecoratorInfo decoratorInfo) {
        cc.method("getPriority", mc -> {
            mc.public_();
            mc.returning(int.class);
            mc.body(bc -> {
                bc.return_(Const.of(decoratorInfo.getPriority()));
            });
        });
    }

    private String packagePrefix(DotName name) {
        String className = name.toString();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * Information about a decorator to be generated.
     */
    public static class DecoratorInfo {
        private final ClassInfo decoratorClass;
        private final Set<Type> decoratedTypes;
        private final ClassInfo delegateClass;
        private final int priority;

        public DecoratorInfo(ClassInfo decoratorClass, Set<Type> decoratedTypes, 
                          ClassInfo delegateClass, int priority) {
            this.decoratorClass = decoratorClass;
            this.decoratedTypes = decoratedTypes;
            this.delegateClass = delegateClass;
            this.priority = priority;
        }

        public ClassInfo getDecoratorClass() {
            return decoratorClass;
        }

        public Set<Type> getDecoratedTypes() {
            return decoratedTypes;
        }

        public ClassInfo getDelegateClass() {
            return delegateClass;
        }

        public int getPriority() {
            return priority;
        }
    }
}
