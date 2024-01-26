package ai.timefold.solver.spring.boot.autoconfigure.dummy.chained.incrementalScoreConstraints;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.calculator.IncrementalScoreCalculator;

public class DummyTestdataChainedSpringIncrementalScore implements IncrementalScoreCalculator {
    @Override
    public void resetWorkingSolution(Object workingSolution) {

    }

    @Override
    public void beforeEntityAdded(Object entity) {

    }

    @Override
    public void afterEntityAdded(Object entity) {

    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {

    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {

    }

    @Override
    public void beforeEntityRemoved(Object entity) {

    }

    @Override
    public void afterEntityRemoved(Object entity) {

    }

    @Override
    public Score calculateScore() {
        return null;
    }
}
