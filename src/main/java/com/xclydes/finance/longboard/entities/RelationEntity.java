package com.xclydes.finance.longboard.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.io.Serializable;

@MappedSuperclass
@Slf4j
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class RelationEntity<I extends Serializable> extends BaseEntity<I> {

    @Column(name = "UPWORK_ID")
    protected String upworkId;
    @Column(name = "WAVE_ID")
    protected String waveId;
}
