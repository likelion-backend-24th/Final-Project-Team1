package com.team1.identity.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrantedRole {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    GrantedRole(Role role, LocalDateTime grantedAt) {
        this.role = role;
        this.grantedAt = grantedAt;
    }

    /*
     * user_roles의 PK가 (user_id, role)이므로 한 사용자 안에서 요소를 구분하는 값은 role뿐이다.
     * @ElementCollection은 컬렉션 변경 여부를 equals로 판단하므로 이 두 메서드가 없으면
     * 매 flush마다 컬렉션이 바뀐 것으로 오인해 같은 행을 다시 INSERT한다.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrantedRole other)) {
            return false;
        }
        return role == other.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(role);
    }
}
