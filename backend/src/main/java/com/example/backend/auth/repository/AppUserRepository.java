package com.example.backend.auth.repository;

import com.example.backend.auth.entity.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Override
    Page<AppUser> findAll(Pageable pageable);

    List<AppUser> findAllByEnabledTrueOrderByFirstNameAscLastNameAsc();
}
