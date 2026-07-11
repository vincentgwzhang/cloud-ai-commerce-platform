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
     * 先把内存中的改动同步到数据库，再执行 DML
     *
     * clearAutomatically = true：
     * DML 执行后清空一级缓存，避免读取到过期实体
     * 
     * 这样可以避免两类问题：
     * 执行修改前：未 flush 的内存修改影响更新条件或结果。
     * 执行修改后：一级缓存中的实体仍然保留旧数据。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    int revokeAllActiveByUserId(@Param("userId") Long userId);
}
