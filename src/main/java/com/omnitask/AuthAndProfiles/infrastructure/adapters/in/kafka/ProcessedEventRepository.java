package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.kafka;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
}
