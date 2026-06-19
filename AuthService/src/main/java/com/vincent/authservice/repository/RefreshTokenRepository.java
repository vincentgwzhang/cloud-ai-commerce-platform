package com.vincent.authservice.repository;

import com.vincent.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    /*
     * 这是一个 JPQL 批量更新语句：它会直接在数据库里执行 UPDATE，
     * 而不是先把每一条 RefreshToken 查出来、修改实体对象、再交给 Hibernate 做脏检查。
     *
     * flushAutomatically = true：
     * 把缓存先写进数据库，保证条件真的成立
     *
     * clearAutomatically = true：
     * 意思是说 更新后把缓存全部丢掉，避免看到缓存旧数据
     * 为什么会缓存数据会过时呢？因为这是 BULK UPDATE, 会绕过 EntityManager
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    int revokeAllActiveByUserId(@Param("userId") Long userId);
}
