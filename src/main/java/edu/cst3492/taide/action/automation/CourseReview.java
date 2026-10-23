package edu.cst3492.taide.action.automation;

import edu.cst3492.taide.rag.RetrievedChunk;

import java.util.List;

public record CourseReview(List<Finding> findings, boolean grounded) {
    public CourseReview {
        findings = List.copyOf(findings);
        if (!grounded && findings.stream().anyMatch(finding -> !finding.sources().isEmpty())) {
            throw new IllegalArgumentException("通用知识审查的引用列表应为空。");
        }
    }

    public enum Severity {
        INFO, WARNING, ERROR
    }

    public record Finding(
            Severity severity,
            String title,
            String explanation,
            String suggestion,
            List<RetrievedChunk> sources
    ) {
        public Finding {
            sources = List.copyOf(sources);
        }
    }
}
