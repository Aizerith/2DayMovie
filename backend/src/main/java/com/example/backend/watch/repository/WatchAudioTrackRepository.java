package com.example.backend.watch.repository;

import com.example.backend.watch.entity.WatchAudioTrack;
import com.example.backend.watch.entity.WatchRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchAudioTrackRepository extends JpaRepository<WatchAudioTrack, Long> {

    List<WatchAudioTrack> findAllByRoomOrderByDisplayOrderAsc(WatchRoom room);

    void deleteAllByRoom(WatchRoom room);
}
