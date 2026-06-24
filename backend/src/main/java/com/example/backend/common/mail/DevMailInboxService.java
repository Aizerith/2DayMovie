package com.example.backend.common.mail;

import com.example.backend.common.config.AppProperties;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class DevMailInboxService {

    private final AppProperties appProperties;
    private final AtomicLong nextId = new AtomicLong(1);
    private final LinkedList<DevMailEntry> entries = new LinkedList<>();

    public DevMailInboxService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public synchronized void record(MailMessage message) {
        if (!appProperties.getMail().isDevInboxEnabled()) {
            return;
        }

        entries.addFirst(new DevMailEntry(
                nextId.getAndIncrement(),
                message.to(),
                message.subject(),
                message.body(),
                LocalDateTime.now()
        ));

        int maxEntries = Math.max(appProperties.getMail().getDevInboxMaxEntries(), 1);
        while (entries.size() > maxEntries) {
            entries.removeLast();
        }
    }

    public synchronized List<DevMailEntry> findAll() {
        return List.copyOf(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
