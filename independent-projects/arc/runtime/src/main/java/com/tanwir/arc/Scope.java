package com.tanwir.arc;

public enum Scope {
    SINGLETON,        // one instance, no proxy, eager ok
    APPLICATION,      // one instance, client proxy, lazy
    REQUEST,          // one instance per HTTP request
    DEPENDENT         // new instance every injection
}
