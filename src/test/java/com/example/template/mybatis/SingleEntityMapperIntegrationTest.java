package com.example.template.mybatis;

import com.example.template.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link SingleEntityMapper}.
 *
 * <p>Validates that the generic-class-based CRUD layer works exactly like
 * {@link BaseMapper} but does not require a {@code @Mapper} interface or any
 * XML configuration. Runs against the in-memory H2 schema in
 * {@code src/test/resources/schema.sql}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
class SingleEntityMapperIntegrationTest {

    @Autowired
    private SingleEntityMapper<User> userMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        // Wipe rows AND reset the auto-increment counter so each test starts
        // from id=1. H2 in-memory DB persists between test methods within the
        // same context; DELETE alone leaves the counter incremented.
        jdbc.execute("TRUNCATE TABLE users");
        BaseMapper.setAuditorProvider(new AuditorProvider() {
            @Override
            public java.time.LocalDateTime now() {
                return java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
            }

            @Override
            public Object currentUser() {
                return "tester";
            }
        });
    }

    @AfterEach
    void restoreAuditor() {
        BaseMapper.setAuditorProvider(AuditorProvider.SYSTEM);
    }

    @Test
    @DisplayName("insert + selectById round-trip")
    void insertAndSelectById() {
        User u = User.builder()
                .username("alice")
                .email("a@x.com")
                .age(20)
                .build();

        int rows = userMapper.insert(u);
        assertEquals(1, rows);
        assertNotNull(u.getId(), "id should be auto-generated and populated on the entity");
        assertEquals(0, u.getDeleted());
        assertEquals("tester", u.getCreatedBy());
        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 0, 0), u.getCreatedAt());

        User fetched = userMapper.selectById(u.getId());
        assertNotNull(fetched);
        assertEquals("alice", fetched.getUsername());
        assertEquals(0, fetched.getDeleted());
    }

    @Test
    @DisplayName("selectById returns null for soft-deleted rows")
    void softDeleteHidesFromSelectById() {
        User u = User.builder().username("bob").email("b@x.com").build();
        userMapper.insert(u);
        Long id = u.getId();

        userMapper.deleteById(id);
        assertNull(userMapper.selectById(id), "Soft-deleted rows must be hidden");
    }

    @Test
    @DisplayName("selectAll excludes soft-deleted rows")
    void selectAllExcludesDeleted() {
        userMapper.insert(User.builder().username("a").build());
        userMapper.insert(User.builder().username("b").build());
        userMapper.insert(User.builder().username("c").build());

        assertEquals(3, userMapper.selectAll().size());

        userMapper.deleteById(1L);
        userMapper.deleteById(2L);
        assertEquals(1, userMapper.selectAll().size());
    }

    @Test
    @DisplayName("selectByIds honours the soft-delete filter")
    void selectByIds() {
        User a = User.builder().username("a").build();
        User b = User.builder().username("b").build();
        User c = User.builder().username("c").build();
        userMapper.insert(a);
        userMapper.insert(b);
        userMapper.insert(c);

        userMapper.deleteById(b.getId());

        List<User> rows = userMapper.selectByIds(List.of(a.getId(), b.getId(), c.getId()));
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(u -> "a".equals(u.getUsername())));
        assertTrue(rows.stream().anyMatch(u -> "c".equals(u.getUsername())));
    }

    @Test
    @DisplayName("updateById stamps @UpdatedAt / @UpdatedBy and skips @CreatedAt / @CreatedBy")
    void updateById() {
        User u = User.builder().username("alice").email("a@x.com").age(20).build();
        userMapper.insert(u);
        Long id = u.getId();
        java.time.LocalDateTime originalCreatedAt = u.getCreatedAt();
        String originalCreatedBy = u.getCreatedBy();

        BaseMapper.setAuditorProvider(new AuditorProvider() {
            @Override
            public java.time.LocalDateTime now() {
                return java.time.LocalDateTime.of(2027, 6, 15, 12, 30);
            }

            @Override
            public Object currentUser() {
                return "updater";
            }
        });

        u.setEmail("a2@x.com");
        u.setAge(21);
        int rows = userMapper.updateById(u);
        assertEquals(1, rows);

        User reloaded = userMapper.selectById(id);
        assertEquals("a2@x.com", reloaded.getEmail());
        assertEquals(21, reloaded.getAge());
        assertEquals(originalCreatedAt, reloaded.getCreatedAt());
        assertEquals(originalCreatedBy, reloaded.getCreatedBy());
        assertEquals(java.time.LocalDateTime.of(2027, 6, 15, 12, 30), reloaded.getUpdatedAt());
        assertEquals("updater", reloaded.getUpdatedBy());
    }

    @Test
    @DisplayName("deleteById performs a soft delete; hardDeleteById really removes the row")
    void deleteBehaviours() {
        User u = User.builder().username("alice").build();
        userMapper.insert(u);

        // Soft delete — flips @LogicDelete to 1.
        assertEquals(1, userMapper.deleteById(u.getId()));
        assertNull(userMapper.selectById(u.getId()));
        assertEquals(0, userMapper.count());

        // Add another row, then hard-delete it.
        userMapper.insert(User.builder().username("bob").build());
        assertEquals(1, userMapper.count());
        Long bobId = userMapper.selectAll().get(0).getId();

        assertEquals(1, userMapper.hardDeleteById(bobId));
        assertEquals(0, userMapper.count());
    }

    @Test
    @DisplayName("count returns the number of non-soft-deleted rows")
    void countRows() {
        assertEquals(0, userMapper.count());

        userMapper.insert(User.builder().username("a").build());
        userMapper.insert(User.builder().username("b").build());
        userMapper.insert(User.builder().username("c").build());
        assertEquals(3, userMapper.count());

        userMapper.deleteById(1L);
        userMapper.deleteById(2L);
        assertEquals(1, userMapper.count());
    }

    @Test
    @DisplayName("Bean resolves the correct entity type at construction time")
    void beanResolvesUserEntityType() {
        // Smoke test: the bean is wired and the underlying entity is the
        // expected one (User). The CRUD tests above already prove that SQL
        // generation works against the users table.
        assertNotNull(userMapper);
        // Insert one row and confirm it round-trips into a User instance.
        userMapper.insert(User.builder().username("smoke").build());
        User fetched = userMapper.selectById(1L);
        assertNotNull(fetched);
        assertEquals("smoke", fetched.getUsername());
        assertFalse(fetched.getEmail() != null && fetched.getEmail().length() > 100);
    }
}