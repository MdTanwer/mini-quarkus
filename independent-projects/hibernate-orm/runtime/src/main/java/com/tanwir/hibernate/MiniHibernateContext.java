package com.tanwir.hibernate;

import org.hibernate.SessionFactory;
import org.jboss.logging.Logger;

/**
 * Holds the booted {@link SessionFactory} (mirrors Quarkus's Hibernate ORM runtime support).
 */
public final class MiniHibernateContext {
    private static final Logger LOG = Logger.getLogger(MiniHibernateContext.class);
    private static volatile SessionFactory sessionFactory;

    private MiniHibernateContext() {}

    public static void setSessionFactory(SessionFactory sf) {
        sessionFactory = sf;
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException("Hibernate ORM: SessionFactory not initialized. "
                    + "Add JPA @Entity class(es) and the mini-quarkus-hibernate-orm extension.");
        }
        return sessionFactory;
    }
}
