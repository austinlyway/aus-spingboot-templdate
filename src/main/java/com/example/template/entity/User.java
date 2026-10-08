package com.example.template.entity;

import com.example.template.mybatis.Column;
import com.example.template.mybatis.CreatedAt;
import com.example.template.mybatis.CreatedBy;
import com.example.template.mybatis.Id;
import com.example.template.mybatis.Ignore;
import com.example.template.mybatis.LogicDelete;
import com.example.template.mybatis.Table;
import com.example.template.mybatis.UpdatedAt;
import com.example.template.mybatis.UpdatedBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Sample entity backed by the {@code users} table.
 *
 * <p>Most fields rely on the default snake_case mapping. Audit / lifecycle fields
 * are picked up by {@link com.example.template.mybatis.BaseMapper} and
 * auto-populated on insert / update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    /** Column name differs from default to show explicit @Column usage. */
    @Column("username")
    private String username;

    private String email;
    private Integer age;

    @CreatedAt
    private LocalDateTime createdAt;

    @UpdatedAt
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @UpdatedBy
    private String updatedBy;

    @LogicDelete
    private Integer deleted;

    /**
     * Example of an ignored field: not persisted, useful for DTO-style enrichment
     * at the controller layer.
     */
    @Ignore
    private transient String fullName;
}