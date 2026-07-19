package io.github.hhagenbuch.castaway.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link Summarizer}: a deterministic, model-free fold-back note. Good enough
 * to prove the mechanism and to keep sync testable offline; a cloud-model summarizer
 * is the natural upgrade when the link is back (mirrors the outbox revalidator).
 */
@Component
public class ConcatSummarizer implements Summarizer {

    @Override
    public String summarize(List<MemoryEvent> foldedBranch) {
        if (foldedBranch.isEmpty()) {
            return "";
        }
        String joined = foldedBranch.stream()
                .map(e -> e.nodeId() + ": " + e.contentJson())
                .collect(Collectors.joining("; "));
        return "While disconnected, this session also recorded elsewhere -> " + joined;
    }
}
