package com.tanwir.miniquarkus.processor;

import java.util.List;

/**
 * ResourceOutput following Quarkus ARC patterns.
 * Handles different types of generated resources.
 */
public interface ResourceOutput {

    List<Resource> getResources();

    /**
     * Represents a generated resource.
     */
    interface Resource {

        /**
         * The type of the resource.
         */
        Type getType();

        /**
         * The name of the resource.
         */
        String getName();

        /**
         * The data of the resource.
         */
        byte[] getData();

        /**
         * Whether this resource is an application class.
         */
        boolean isApplicationClass();

        /**
         * Special types for generated classes.
         */
        SpecialType getSpecialType();

        /**
         * Resource types.
         */
        enum Type {
            JAVA_CLASS,
            RESOURCE
        }

        /**
         * Special types for generated classes.
         */
        enum SpecialType {
            BEAN,
            CLIENT_PROXY,
            SUBCLASS,
            INTERCEPTOR,
            SYNTHETIC_BEAN
        }
    }

    /**
     * Implementation of Resource.
     */
    class ResourceImpl implements Resource {

        private final Type type;
        private final String name;
        private final byte[] data;
        private final boolean applicationClass;
        private final SpecialType specialType;

        private ResourceImpl(Type type, String name, byte[] data, boolean applicationClass, SpecialType specialType) {
            this.type = type;
            this.name = name;
            this.data = data;
            this.applicationClass = applicationClass;
            this.specialType = specialType;
        }

        static Resource javaClass(String name, byte[] data, SpecialType specialType, boolean applicationClass, String path) {
            return new ResourceImpl(Type.JAVA_CLASS, name, data, applicationClass, specialType);
        }

        static Resource resource(String name, byte[] data) {
            return new ResourceImpl(Type.RESOURCE, name, data, false, null);
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public byte[] getData() {
            return data;
        }

        @Override
        public boolean isApplicationClass() {
            return applicationClass;
        }

        @Override
        public SpecialType getSpecialType() {
            return specialType;
        }
    }
}
