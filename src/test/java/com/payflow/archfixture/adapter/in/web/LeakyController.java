package com.payflow.archfixture.adapter.in.web;

import com.payflow.archfixture.adapter.out.persistence.LeakyJpaRepository;

/** Deliberate violation fixture: a controller talking to a persistence repository directly. */
public class LeakyController {

    private final LeakyJpaRepository repository;

    public LeakyController(LeakyJpaRepository repository) {
        this.repository = repository;
    }

    public Object find() {
        return repository.findAll();
    }
}
