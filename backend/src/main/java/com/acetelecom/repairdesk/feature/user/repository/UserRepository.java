package com.acetelecom.repairdesk.feature.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username) AND u.status <> :deleted")
    Optional<User> findActiveByUsername(@Param("username") String username,
            @Param("deleted") AccountStatus deleted);

    default Optional<User> findActiveByUsername(String username) {
        return findActiveByUsername(username, AccountStatus.DELETED);
    }

    @Query("SELECT u FROM User u WHERE u.mobile = :mobile AND u.status <> :deleted")
    Optional<User> findActiveByMobile(@Param("mobile") String mobile,
            @Param("deleted") AccountStatus deleted);

    default Optional<User> findActiveByMobile(String mobile) {
        return findActiveByMobile(mobile, AccountStatus.DELETED);
    }

    boolean existsByStatusNot(AccountStatus status);

    List<User> findByRoleAndStatusNotOrderByNameAsc(Role role, AccountStatus excluded);

    default List<User> findActiveByRole(Role role) {
        return findByRoleAndStatusNotOrderByNameAsc(role, AccountStatus.DELETED);
    }

    List<User> findByStatusNotOrderByRoleAscUsernameAsc(AccountStatus excluded);

    default List<User> findAllNonDeleted() {
        return findByStatusNotOrderByRoleAscUsernameAsc(AccountStatus.DELETED);
    }
}
