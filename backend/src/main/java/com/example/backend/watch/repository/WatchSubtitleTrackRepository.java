package com.example.backend.watch.repository;

import com.example.backend.watch.entity.WatchRoom;
import com.example.backend.watch.entity.WatchSubtitleTrack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchSubtitleTrackRepository extends JpaRepository<WatchSubtitleTrack, Long> {

    List<WatchSubtitleTrack> findAllByRoomOrderByDisplayOrderAsc(WatchRoom room);

    void deleteAllByRoom(WatchRoom room);
}
