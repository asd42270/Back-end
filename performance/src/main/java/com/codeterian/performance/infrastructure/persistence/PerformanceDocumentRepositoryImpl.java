package com.codeterian.performance.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.codeterian.performance.domain.performance.PerformanceDocument;

public interface PerformanceDocumentRepositoryImpl extends ElasticsearchRepository<PerformanceDocument, UUID> {
	List<PerformanceDocument>
	findByTitleContainingOrDescriptionContainingOrLocationContaining(String title, String description, String location);
}
