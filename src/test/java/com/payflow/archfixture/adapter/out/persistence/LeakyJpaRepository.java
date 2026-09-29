package com.payflow.archfixture.adapter.out.persistence;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Deliberate violation fixture: a public Spring Data repository used by a controller.
 * {@code @NoRepositoryBean} keeps Spring Data from instantiating it in integration tests.
 */
@NoRepositoryBean
public interface LeakyJpaRepository extends Repository<Object, UUID> {

    Iterable<Object> findAll();
}
