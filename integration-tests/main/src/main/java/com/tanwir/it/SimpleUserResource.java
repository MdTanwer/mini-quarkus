package com.tanwir.it;

import com.tanwir.arc.Singleton;
import com.tanwir.resteasy.reactive.server.*;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Singleton
@Path("/simple-users")
public class SimpleUserResource {

    private final Map<Long, User> users = new HashMap<>();
    private long nextId = 1;

    public SimpleUserResource() {
        // Initialize with some test data
        users.put(1L, new User(1L, "John Doe", "john@example.com"));
        users.put(2L, new User(2L, "Jane Smith", "jane@example.com"));
        nextId = 3;
    }

    @GET
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    @GET
    @Path("/{id}")
    public User getUser() {
        return users.get(1L); // Simplified for Phase 3 demo
    }

    @POST
    public Response createUser() {
        User newUser = new User(nextId++, "New User", "new@example.com");
        users.put(newUser.getId(), newUser);
        return Response.created(newUser);
    }

    @PUT
    @Path("/{id}")
    public Response updateUser() {
        User user = users.get(1L);
        if (user != null) {
            user.setName("Updated User");
            return Response.ok(user);
        }
        return Response.status(404);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser() {
        if (users.containsKey(1L)) {
            users.remove(1L);
            return Response.noContent();
        }
        return Response.status(404);
    }

    @GET
    @Path("/search")
    public List<User> searchUsers() {
        return getAllUsers(); // Simplified for Phase 3 demo
    }

    @PATCH
    @Path("/{id}")
    public Response patchUser() {
        User user = users.get(1L);
        if (user != null) {
            user.setName("Patched User");
            return Response.ok(user);
        }
        return Response.status(404);
    }
}
