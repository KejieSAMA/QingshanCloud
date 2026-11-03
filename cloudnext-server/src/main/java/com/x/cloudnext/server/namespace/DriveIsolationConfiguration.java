package com.x.cloudnext.server.namespace;

import java.io.IOException;
import javax.persistence.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.jpa.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class DriveIsolationConfiguration {
    @Bean(name = "transactionManager")
    public JpaTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory) {
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
                super.doBegin(transaction, definition);
                EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
                if (holder != null) DriveContext.apply(holder.getEntityManager());
            }
        };
    }

    @Component @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    public static class RequestBoundary extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            DriveContext.clear();
            try { chain.doFilter(request, response); }
            finally { DriveContext.clear(); }
        }
    }
}
