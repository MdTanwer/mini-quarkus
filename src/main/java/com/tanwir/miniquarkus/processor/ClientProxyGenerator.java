package com.tanwir.miniquarkus.processor;

import static java.lang.constant.ConstantDescs.CD_Object;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.classDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.methodDescOf;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;


import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableContext;
import io.quarkus.arc.impl.BuiltinScope;
import io.quarkus.arc.impl.Mockable;
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
import io.quarkus.gizmo2.desc.InterfaceMethodDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * ClientProxyGenerator following Quarkus ARC patterns.
 * Generates client proxy classes that implement delegation and mocking functionality.
 */
public class ClientProxyGenerator extends AbstractGenerator {

    static final String CLIENT_PROXY_SUFFIX = "_ClientProxy";
    static final String DELEGATE_METHOD_NAME = "arc$delegate";
    static final String SET_MOCK_METHOD_NAME = "arc$setMock";
    static final String CLEAR_MOCK_METHOD_NAME = "arc$clearMock";
    static final String GET_CONTEXTUAL_INSTANCE_METHOD_NAME = "arc_contextualInstance";
    static final String GET_BEAN = "arc_bean";
    static final String BEAN_FIELD = "bean";
    static final String MOCK_FIELD = "mock";
    static final String CONTEXT_FIELD = "context";

    private final Predicate<DotName> applicationClassPredicate;
    private final boolean mockable;
    private final Set<String> existingClasses;
    private final Set<DotName> singleContextNormalScopes;

    public ClientProxyGenerator(boolean generateSources, boolean mockable, ReflectionRegistration reflectionRegistration,
                               Predicate<DotName> applicationClassPredicate, Set<String> existingClasses,
                               Set<DotName> singleContextNormalScopes) {
        super(generateSources, reflectionRegistration);
        this.applicationClassPredicate = applicationClassPredicate;
        this.mockable = mockable;
        this.existingClasses = existingClasses;
        this.singleContextNormalScopes = singleContextNormalScopes;
    }

    /**
     * Generates a client proxy class for the given bean.
     */
    public Collection<Resource> generate(BeanInfo bean, String beanClassName) {
        String baseName = getBeanBaseName(beanClassName);
        String targetPackage = getClientProxyPackageName(bean);
        String generatedName = generatedNameFromTarget(targetPackage, baseName, CLIENT_PROXY_SUFFIX);
        
        if (existingClasses.contains(generatedName)) {
            return Collections.emptyList();
        }

        boolean isApplicationClass = applicationClassPredicate.test(getApplicationClassTestName(bean));
        ResourceClassOutput classOutput = createResourceClassOutput(isApplicationClass,
                name -> name.equals(generatedName) ? SpecialType.CLIENT_PROXY : null);

        Gizmo gizmo = gizmo(classOutput);

        createClientProxy(gizmo, bean, generatedName, targetPackage);

        return classOutput.getResources();
    }

    private void createClientProxy(Gizmo gizmo, BeanInfo bean, String generatedName, String targetPackage) {
        Type providerType = bean.getProviderType();
        ClassInfo providerClass = getClassFromType(providerType);
        ClassDesc providerClassDesc = classDescOf(providerClass);

        boolean isInterface = providerClass.isInterface();
        ClassDesc superClass = isInterface ? CD_Object : providerClassDesc;

        // Foo_ClientProxy extends Foo implements ClientProxy
        gizmo.class_(generatedName, cc -> {
            if (!isInterface) {
                cc.extends_(providerClassDesc);
            } else {
                cc.implements_(providerClassDesc);
            }
            cc.implements_(classDescOf(ClientProxy.class));
            
            if (mockable) {
                cc.implements_(classDescOf(Mockable.class));
            }

            // Generate fields
            FieldDesc beanField = generateBeanField(cc);
            FieldDesc mockField = generateMockField(cc, providerClassDesc);
            FieldDesc contextField = generateContextField(cc, bean);

            // Generate constructor
            generateConstructor(cc, bean, beanField, contextField, providerClassDesc);

            // Generate delegate method
            generateDelegateMethod(cc, bean, beanField, mockField, contextField, providerClassDesc);

            // Generate mock management methods
            if (mockable) {
                generateSetMockMethod(cc, mockField, providerClassDesc);
                generateClearMockMethod(cc, mockField);
            }

            // Generate business methods
            generateBusinessMethods(cc, bean, providerClassDesc, isInterface);

            // Generate ClientProxy interface methods
            generateClientProxyMethods(cc, beanField, contextField);
        });
    }

    private FieldDesc generateBeanField(ClassCreator cc) {
        return cc.field(BEAN_FIELD, fc -> {
            fc.private_();
            fc.final_();
            fc.setType(classDescOf(InjectableBean.class));
        });
    }

    private ClassDesc currentProviderClassDesc;

    private FieldDesc generateMockField(ClassCreator cc, ClassDesc providerClassDesc) {
        if (mockable) {
            this.currentProviderClassDesc = providerClassDesc;
            return cc.field(MOCK_FIELD, fc -> {
                fc.private_();
                fc.volatile_();
                fc.setType(providerClassDesc);
            });
        }
        return null;
    }

    private FieldDesc generateContextField(ClassCreator cc, BeanInfo bean) {
        if (isApplicationScope(bean) || hasSingleContext(bean)) {
            return cc.field(CONTEXT_FIELD, fc -> {
                fc.private_();
                fc.final_();
                fc.setType(classDescOf(InjectableContext.class));
            });
        }
        return null;
    }

    private void generateConstructor(ClassCreator cc, BeanInfo bean, FieldDesc beanField, 
                                   FieldDesc contextField, ClassDesc providerClassDesc) {
        cc.constructor(mc -> {
            ParamVar idParam = mc.parameter("id", String.class);
            mc.body(bc -> {
                // Invoke super constructor
                bc.invokeSpecial(ConstructorDesc.of(providerClassDesc.equals(CD_Object) ? Object.class : providerClassDesc), cc.this_());

                // Get Arc container
                LocalVar arc = bc.localVar("arc", bc.invokeStatic(MethodDesc.of("io.quarkus.arc.Arc", "container", classDescOf("io.quarkus.arc.ArcContainer"))));
                
                // Get bean from container
                LocalVar beanVar = bc.localVar("bean", bc.invokeInterface(
                    MethodDesc.of(classDescOf("io.quarkus.arc.ArcContainer"), "bean", classDescOf(InjectableBean.class), String.class),
                    arc, idParam));
                
                bc.set(cc.this_().field(beanField), beanVar);

                // Set context if applicable
                if (contextField != null) {
                    Expr scope = bc.invokeInterface(
                        MethodDesc.of(classDescOf(InjectableBean.class), "getScope", ClassDesc.of(Class.class)),
                        cc.this_().field(beanField));
                    Expr contexts = bc.invokeInterface(
                        MethodDesc.of(classDescOf("io.quarkus.arc.ArcContainer"), "getActiveContexts", classDescOf("java.util.List"), ClassDesc.of(Class.class)),
                        arc, scope);
                    
                    bc.set(cc.this_().field(contextField), bc.withList(contexts).get(0));
                }

                bc.return_();
            });
        });
    }

    private void generateDelegateMethod(ClassCreator cc, BeanInfo bean, FieldDesc beanField, 
                                      FieldDesc mockField, FieldDesc contextField, ClassDesc providerClassDesc) {
        cc.method(DELEGATE_METHOD_NAME, mc -> {
            mc.private_();
            mc.returning(providerClassDesc);
            mc.body(bc -> {
                if (mockable && mockField != null) {
                    // If mockable and mocked, return the mock
                    bc.if_(bc.notNull(cc.this_().field(mockField)), thenBlock -> {
                        thenBlock.return_(cc.this_().field(mockField));
                    });
                }

                // Get instance from context
                Expr instance;
                if (contextField != null) {
                    // Use stored context
                    instance = bc.invokeInterface(
                        MethodDesc.of(classDescOf(InjectableContext.class), "get", Object.class, classDescOf(InjectableBean.class)),
                        cc.this_().field(contextField), cc.this_().field(beanField));
                } else {
                    // Get active context
                    LocalVar arc = bc.localVar("arc", bc.invokeStatic(MethodDesc.of("io.quarkus.arc.Arc", "container", classDescOf("io.quarkus.arc.ArcContainer"))));
                    Expr scope = bc.invokeInterface(
                        MethodDesc.of(classDescOf(InjectableBean.class), "getScope", ClassDesc.of(Class.class)),
                        cc.this_().field(beanField));
                    Expr context = bc.invokeInterface(
                        MethodDesc.of(classDescOf("io.quarkus.arc.ArcContainer"), "getActiveContext", classDescOf(InjectableContext.class), ClassDesc.of(Class.class)),
                        arc, scope);
                    
                    instance = bc.invokeInterface(
                        MethodDesc.of(classDescOf(InjectableContext.class), "get", Object.class, classDescOf(InjectableBean.class)),
                        context, cc.this_().field(beanField));
                }

                bc.return_(bc.checkCast(instance, providerClassDesc));
            });
        });
    }

    private void generateSetMockMethod(ClassCreator cc, FieldDesc mockField, ClassDesc providerClassDesc) {
        cc.method(SET_MOCK_METHOD_NAME, mc -> {
            mc.public_();
            mc.returning(void.class);
            ParamVar mockParam = mc.parameter("mock", providerClassDesc);
            mc.body(bc -> {
                bc.set(cc.this_().field(mockField), mockParam);
                bc.return_();
            });
        });
    }

    private void generateClearMockMethod(ClassCreator cc, FieldDesc mockField) {
        cc.method(CLEAR_MOCK_METHOD_NAME, mc -> {
            mc.public_();
            mc.returning(void.class);
            mc.body(bc -> {
                bc.set(cc.this_().field(mockField), Const.ofNull(currentProviderClassDesc));
                bc.return_();
            });
        });
    }

    private void generateBusinessMethods(ClassCreator cc, BeanInfo bean, ClassDesc providerClassDesc, boolean isInterface) {
        ClassInfo providerClass = getClassFromType(bean.getProviderType());
        if (providerClass == null) {
            return;
        }
        for (MethodInfo method : providerClass.methods()) {
            if (method.isSynthetic() || method.isConstructor()
                    || java.lang.reflect.Modifier.isStatic(method.flags())
                    || java.lang.reflect.Modifier.isPrivate(method.flags())
                    || java.lang.reflect.Modifier.isFinal(method.flags())) {
                continue;
            }
            MethodDesc methodDesc = methodDescOf(method);
            cc.method(methodDesc, mc -> {
                mc.public_();
                List<ParamVar> params = new ArrayList<>();
                for (int i = 0; i < method.parametersCount(); i++) {
                    params.add(mc.parameter("p" + i, classDescOf(method.parameterType(i))));
                }
                mc.body(bc -> {
                    Expr delegate = bc.invokeVirtual(
                        MethodDesc.of(cc.type(), DELEGATE_METHOD_NAME, providerClassDesc),
                        cc.this_());
                    if (isInterface) {
                        Expr result = bc.invokeInterface(methodDesc, delegate, params.toArray(new Expr[0]));
                        if (method.returnType().kind() == org.jboss.jandex.Type.Kind.VOID) {
                            bc.return_();
                        } else {
                            bc.return_(result);
                        }
                    } else {
                        Expr result = bc.invokeVirtual(methodDesc, delegate, params.toArray(new Expr[0]));
                        if (method.returnType().kind() == org.jboss.jandex.Type.Kind.VOID) {
                            bc.return_();
                        } else {
                            bc.return_(result);
                        }
                    }
                });
            });
        }
    }

    private void generateClientProxyMethods(ClassCreator cc, FieldDesc beanField, FieldDesc contextField) {
        // Get bean method
        cc.method(GET_BEAN, mc -> {
            mc.public_();
            mc.returning(classDescOf(InjectableBean.class));
            mc.body(bc -> {
                bc.return_(cc.this_().field(beanField));
            });
        });

        // Get contextual instance method
        cc.method(GET_CONTEXTUAL_INSTANCE_METHOD_NAME, mc -> {
            mc.public_();
            mc.returning(Object.class);
            mc.body(bc -> {
                Expr delegate = bc.invokeVirtual(
                    MethodDesc.of(cc.type(), DELEGATE_METHOD_NAME, Object.class),
                    cc.this_());
                bc.return_(delegate);
            });
        });
    }

    // Helper methods
    private boolean isApplicationScope(BeanInfo bean) {
        return BuiltinScope.APPLICATION.getDotName().equals(bean.getScope());
    }

    private boolean hasSingleContext(BeanInfo bean) {
        return singleContextNormalScopes.contains(bean.getScope());
    }

    private String getClientProxyPackageName(BeanInfo bean) {
        return packagePrefix(bean.getProviderType().name()) + ".client";
    }

    private DotName getApplicationClassTestName(BeanInfo bean) {
        return bean.getBeanClass();
    }

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
