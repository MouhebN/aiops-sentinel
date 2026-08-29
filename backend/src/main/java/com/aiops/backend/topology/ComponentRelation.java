package com.aiops.backend.topology;

import com.aiops.backend.component.MonitoredComponent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "component_relations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_component_relation_endpoints",
                columnNames = {"source_component_id", "target_component_id", "relation_type"}
        )
)
public class ComponentRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_component_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MonitoredComponent source;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "target_component_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MonitoredComponent target;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 40)
    private ComponentRelationType relationType;

    @Column(length = 200)
    private String label;

    @Column(nullable = false)
    private Instant createdAt;

    protected ComponentRelation() {
    }

    public ComponentRelation(
            MonitoredComponent source,
            MonitoredComponent target,
            ComponentRelationType relationType,
            String label
    ) {
        this.source = source;
        this.target = target;
        this.relationType = relationType;
        this.label = label;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public MonitoredComponent getSource() {
        return source;
    }

    public MonitoredComponent getTarget() {
        return target;
    }

    public ComponentRelationType getRelationType() {
        return relationType;
    }

    public String getLabel() {
        return label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
