package com.example.backend.watch.repository;

import com.example.backend.watch.entity.WatchRoom;
import com.example.backend.watch.entity.WatchRoomStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchRoomRepository extends JpaRepository<WatchRoom, Long> {

    Optional<WatchRoom> findByShareCodeIgnoreCase(String shareCode);

    List<WatchRoom> findAllByStatus(WatchRoomStatus status);

    boolean existsByShareCodeIgnoreCase(String shareCode);
}
