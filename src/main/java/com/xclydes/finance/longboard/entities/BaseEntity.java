package com.xclydes.finance.longboard.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@MappedSuperclass
@Data
@Slf4j
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class BaseEntity<I extends Serializable> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    protected I id;

    @JsonIgnore
    @Column(name = "CREATED_AT")
    protected Date createdAt;

    @JsonIgnore
    @Column(name = "UPDATED_AT")
    protected Date updatedAt;

    @JsonIgnore
    @Column(name = "DELETED_AT")
    protected Date deletedAt;

    @PrePersist
    public void preSave() {
        if (this.getCreatedAt() == null) {
            this.setCreatedAt(new Date());
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (this.getUpdatedAt() == null) {
            this.setUpdatedAt(new Date());
        }
    }

    @PreRemove
    public void preDelete() {
        if (this.getDeletedAt() == null) {
            this.setDeletedAt(new Date());
        }
    }

}
