package com.brightline.monitor.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "integration")
public class Integration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Expected minutes between requests; drives the silence rule. */
    @Column(nullable = false)
    private int expectedCadenceMin;

    protected Integration() {}

    public Integration(String name, int expectedCadenceMin) {
        this.name = name;
        this.expectedCadenceMin = expectedCadenceMin;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getExpectedCadenceMin() { return expectedCadenceMin; }
}
