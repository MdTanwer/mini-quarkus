package com.tanwir.panache;

import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface for {@link MiniEntity}-annotated types.
 *
 * <p>Mirrors {@code io.quarkus.hibernate.orm.panache.PanacheRepository} from real Quarkus.
 * Implement this interface (or extend {@link MiniRepositoryBase}) to get a full set of
 * CRUD operations for an entity type — without writing any SQL.
 *
 * <h2>Real Quarkus comparison</h2>
 * <table>
 *   <tr><th>mini-quarkus</th><th>real Quarkus / Panache</th></tr>
 *   <tr><td>{@link MiniRepository}</td><td>{@code PanacheRepository}</td></tr>
 *   <tr><td>{@link MiniRepositoryBase}</td><td>default methods on {@code PanacheRepository}</td></tr>
 *   <tr><td>JDBC + reflection</td><td>Hibernate ORM (JPA + bytecode enhancement)</td></tr>
 *   <tr><td>H2 in-memory</td><td>Any JPA-supported database</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Singleton
 * public class ProductRepository extends MiniRepositoryBase<Product> {
 *     public ProductRepository() { super(Product.class); }
 *
 *     public List<Product> findByName(String name) {
 *         return findWhere("name = ?", name);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the entity type; must be annotated with {@link MiniEntity}
 */
public interface MiniRepository<T> {

    /** Finds an entity by its {@link Id}-annotated primary key. */
    Optional<T> findById(long id);

    /** Returns all entities in the table. */
    List<T> findAll();

    /**
     * Persists a new entity and returns the saved instance with the generated
     * {@link Id} populated.
     */
    T persist(T entity);

    /** Updates an existing entity (by its {@link Id}) with the current field values. */
    T update(T entity);

    /** Deletes an entity by its {@link Id}. */
    void delete(T entity);

    /** Deletes an entity by primary key value. */
    void deleteById(long id);

    /** Returns the total number of rows in the table. */
    long count();
}
