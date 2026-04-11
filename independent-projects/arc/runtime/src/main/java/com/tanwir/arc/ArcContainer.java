package com.tanwir.arc;

public interface ArcContainer {

    <T> InstanceHandle<T> instance(Class<T> type);
}
