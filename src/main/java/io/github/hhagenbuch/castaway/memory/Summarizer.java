package io.github.hhagenbuch.castaway.memory;

import java.util.List;

/**
 * Folds a losing divergent branch into a single context note. An interface so the
 * default (concatenation) can be swapped for a cloud-model summarization pass — the
 * DESIGN.md section 2.5 fold-back — without the sync logic depending on a model.
 */
public interface Summarizer {

    String summarize(List<MemoryEvent> foldedBranch);
}
