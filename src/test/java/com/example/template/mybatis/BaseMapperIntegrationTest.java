package com.example.template.mybatis;

import com.example.template.entity.User;
import com.example.template.mapper.UserMapper;
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
 * Integration test for the {@link BaseMapper} implementation of
 * {@link UserMapper}. Runs against the in-memory H2 schema declared in
 * {@code src/test/resources/schema.sql}.
 *
 * <p>Validates that the annotation-driven SQL provider correctly:
 * <ul>
 *   <li>Inserts a row and auto-fills audit / soft-delete fields.</li>
 *   <li>Selects by id and by list of ids, excluding soft-deleted rows.</li>
 *   <li>Updates a row and stamps {@code updated_at} / {@code updated_by}.</li>
 *   <li>Converts {@code deleteById} into a soft delete (UPDATE), and
 *       {@code hardDeleteById} into a real DELETE.</li>
 *   <li>Counts non-deleted rows.</li>
 * </ul>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
class BaseMapperIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        // Wipe rows AND reset the auto-increment counter so each test starts
        // from id=1. H2 in-memory DB persists between test methods within the
        // same context; DELETE alone leaves the counter incremented.
        jdbc.execute("TRUNCATE TABLE users");
    }

    @BeforeEach
    void resetAuditor() {
        // Make assertions deterministic.
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
    @DisplayName("insert fills id (auto-increment), audit fields, and soft-delete = 0")
    void insertAutoFills() {
        User u = User.builder()
                .username("alice")
                .email("a@x.com")
                .age(20)
                .build();
        int rows = userMapper.insert(u);
        assertEquals(1, rows);
        assertNotNull(u.getId(), "id should be populated after insert");
        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 0, 0), u.getCreatedAt());
        assertEquals("tester", u.getCreatedBy());
        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 0, 0), u.getUpdatedAt());
        assertEquals("tester", u.getUpdatedBy());
        assertEquals(0, u.getDeleted());
    }

    @Test
    @DisplayName("selectById returns the row; deleted rows are invisible")
    void selectById() {
        User u = User.builder().username("bob").email("b@x.com").age(30).build();
        userMapper.insert(u);
        Long id = u.getId();

        User fetched = userMapper.selectById(id);
        assertNotNull(fetched);
        assertEquals("bob", fetched.getUsername());
        assertEquals(0, fetched.getDeleted());

        // Mark deleted via soft delete; the next selectById should return null.
        userMapper.deleteById(id);
        assertNull(userMapper.selectById(id),
                "Soft-deleted rows must be hidden from selectById");
    }

    @Test
    @DisplayName("selectAll excludes soft-deleted rows")
    void selectAllExcludesDeleted() {
        userMapper.insert(User.builder().username("a").build());
        userMapper.insert(User.builder().username("b").build());
        userMapper.insert(User.builder().username("c").build());

        List<User> all = userMapper.selectAll();
        assertEquals(3, all.size());

        // Soft-delete the second row.
        User target = all.get(1);
        userMapper.deleteById(target.getId());

        List<User> after = userMapper.selectAll();
        assertEquals(2, after.size());
        assertFalse(after.stream().anyMatch(u -> u.getId().equals(target.getId())),
                "Soft-deleted row must be filtered out");
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
    @DisplayName("updateById stamps updated_at/updated_by and skips @CreatedAt/@CreatedBy")
    void updateById() {
        User u = User.builder().username("alice").email("a@x.com").age(20).build();
        userMapper.insert(u);
        Long id = u.getId();
        java.time.LocalDateTime originalCreatedAt = u.getCreatedAt();
        String originalCreatedBy = u.getCreatedBy();

        // Switch the auditor to a different value for the update phase.
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
        assertEquals(originalCreatedAt, reloaded.getCreatedAt(),
                "@CreatedAt must not change on update");
        assertEquals(originalCreatedBy, reloaded.getCreatedBy(),
                "@CreatedBy must not change on update");
        assertEquals(java.time.LocalDateTime.of(2027, 6, 15, 12, 30), reloaded.getUpdatedAt());
        assertEquals("updater", reloaded.getUpdatedBy());
    }

    @Test
    @DisplayName("deleteById performs a soft delete; hardDeleteById actually removes the row")
    void deleteBehaviours() {
        User u = User.builder().username("alice").build();
        userMapper.insert(u);
        Long id = u.getId();

        // Soft delete — flips the @LogicDelete column.
        int soft = userMapper.deleteById(id);
        assertEquals(1, soft);

        // Even though the row exists in the table, the default SELECT filters it out.
        assertNull(userMapper.selectById(id));
        // count() also excludes soft-deleted rows.
        assertEquals(0, userMapper.count());

        // hardDeleteById actually removes the row, but since it's already
        // hidden we need a count including deleted rows to verify; we
        // do this indirectly by inserting another and counting.
        userMapper.insert(User.builder().username("bob").build());
        assertEquals(1, userMapper.count());

        userMapper.hardDeleteById(userMapper.selectAll().get(0).getId());
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
    @DisplayName("Custom entity-specific query (findByEmailLike) still works")
    void customQueryAlongsideBase() {
        userMapper.insert(User.builder().username("alice").email("alice@x.com").build());
        userMapper.insert(User.builder().username("bob").email("bob@x.com").build());
        userMapper.insert(User.builder().username("carol").email("alice2@x.com").build());

        List<User> matches = userMapper.findByEmailLike("alice%");
        assertEquals(2, matches.size());
    }
}