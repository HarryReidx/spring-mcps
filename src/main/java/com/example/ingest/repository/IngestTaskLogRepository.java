package com.example.ingest.repository;

import com.example.ingest.entity.IngestTaskLog;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface IngestTaskLogRepository extends CrudRepository<IngestTaskLog, UUID> {
    List<IngestTaskLog> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
