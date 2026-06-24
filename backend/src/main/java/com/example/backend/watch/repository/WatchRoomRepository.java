package com.example.backend.watch.repository;

import com.example.backend.watch.entity.WatchRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchRoomRepository extends JpaRepository<WatchRoom, Long> {

    Optional<WatchRoom> findByShareCodeIgnoreCase(String shareCode);

    boolean existsByShareCodeIgnoreCase(String shareCode);
}
