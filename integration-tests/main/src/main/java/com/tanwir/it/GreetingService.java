package com.tanwir.it;

import com.tanwir.arc.Singleton;

@Singleton
public final class GreetingService {

    public String message() {
        return "mini-quarkus GET works";
    }
}
