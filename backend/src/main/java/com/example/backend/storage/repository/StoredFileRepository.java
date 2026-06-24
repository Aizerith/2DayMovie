package com.example.backend.storage.repository;

import com.example.backend.storage.entity.StoredFile;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<StoredFile> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<StoredFile> findAllByOwnerId(Long ownerId, Pageable pageable);

    List<StoredFile> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    List<StoredFile> findAllByOrderByUpdatedAtDesc();
}
