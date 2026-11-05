package com.omnitask.AuthAndProfiles.infrastructure.adapters.in.rabbitmq;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
}
