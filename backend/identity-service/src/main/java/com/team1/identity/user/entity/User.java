package com.team1.identity.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    private Set<GrantedRole> roles = new LinkedHashSet<>();

    private User(String email, String passwordHash, String name, Role role, LocalDateTime now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = now;
        this.roles.add(new GrantedRole(role, now));
    }

    public static User create(String email, String passwordHash, String name, Role role, LocalDateTime now) {
        return new User(email, passwordHash, name, role, now);
    }

    /*
     * 계약서의 JWT 클레임 role은 단수다. Sprint 1은 1인 1Role이지만
     * 여러 개가 부여된 경우에도 결과가 흔들리지 않도록 가장 강한 Role을 고른다.
     * (enum 선언 순서: USER < ORGANIZER < SUPER_ADMIN)
     */
    public Role primaryRole() {
        return roles.stream()
                .map(GrantedRole::getRole)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElseThrow(() -> new IllegalStateException("Role이 없는 사용자입니다. id=" + id));
    }
}
