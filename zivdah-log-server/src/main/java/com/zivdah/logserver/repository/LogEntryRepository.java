package com.zivdah.logserver.repository;

import com.zivdah.logserver.entity.LogEntry;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface LogEntryRepository extends ReactiveCrudRepository<LogEntry, Long> {
}
