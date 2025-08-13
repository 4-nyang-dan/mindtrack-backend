package com.example.mindtrack.Repository;

import com.example.mindtrack.Domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByUserId(String userId);
}
