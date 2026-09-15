package ai.timefold.solver.core.impl.localsearch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchType;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.monitoring.SolverMetric;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.heuristic.move.SelectorBasedDummyMove;
import ai.timefold.solver.core.impl.localsearch.decider.forager.AcceptedLocalSearchForager;
import ai.timefold.solver.core.impl.localsearch.decider.forager.finalist.HighestScoreFinalistPodium;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.timefold.solver.core.impl.score.definition.SimpleScoreDefinition;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.testdomain.TestdataSolution;
import ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldConstraintProvider;
import ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldEntity;
import ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldSolution;
import ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldValue;
import ai.timefold.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

/**
 * A step whose moves were all rejected still executes one of them, and if that move leaves the solution
 * structurally flawed, {@link ai.timefold.solver.core.impl.move.MoveDirector#execute} throws
 * {@code The move (...) caused the solution to become structurally flawed.} and the solve dies.
 * <p>
 * The path there runs through three places that each look reasonable on their own:
 * <ol>
 * <li>{@code AbstractAcceptor.isAccepted} rejects every structurally flawed move, as it should.</li>
 * <li>{@code AcceptedLocalSearchForager.addMove} hands the podium every move it is given, accepted or not,
 * and {@code HighestScoreFinalistPodium.addMove} keeps an unaccepted one as long as no accepted move has
 * turned up - a fallback so that a step always has something to do.</li>
 * <li>{@code AcceptedLocalSearchForager.pickMove} returns that fallback finalist, and
 * {@code DefaultLocalSearchPhase.doStep} executes it.</li>
 * </ol>
 * A step ends with nothing accepted whenever termination fires in the middle of it, which is why the
 * end-to-end reproducer below is sensitive to the exact move count limit rather than to the problem alone.
 */
class StructurallyFlawedStepTest {

    /**
     * Jobs on machines, where a job may not start before the job it depends on has finished. Placing a job
     * ahead of its own dependency on the same machine makes the shadow variable graph cyclic, so the local
     * search meets structurally flawed moves constantly and rejects them.
     * <p>
     * The move count limit is what makes this fail: it stops the phase part way through a step whose moves
     * were all rejected. 190_000 finishes cleanly, 200_000 does not.
     */
    @Test
    void solveDoesNotExecuteAStructurallyFlawedMove() {
        var problem = buildProblem(5, 5, 8);
        var solverConfig = new SolverConfig()
                .withSolutionClass(TestdataDependencyNoInconsistentFieldSolution.class)
                .withEntityClasses(TestdataDependencyNoInconsistentFieldEntity.class,
                        TestdataDependencyNoInconsistentFieldValue.class)
                .withConstraintProviderClass(TestdataDependencyNoInconsistentFieldConstraintProvider.class)
                .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
                .withPhases(new ConstructionHeuristicPhaseConfig(),
                        new LocalSearchPhaseConfig()
                                .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
                                .withMoveSelectorConfig(new UnionMoveSelectorConfig(List.of(
                                        new ListChangeMoveSelectorConfig(),
                                        new ListSwapMoveSelectorConfig()))))
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(200_000L));
        var solver = SolverFactory.<TestdataDependencyNoInconsistentFieldSolution> create(solverConfig)
                .buildSolver();

        assertThatCode(() -> solver.solve(problem)).doesNotThrowAnyException();
    }

    /**
     * The same defect without a solve around it: a single rejected, structurally flawed move is the only
     * thing the forager sees, and it hands it back as the step to execute.
     */
    @Test
    void foragerDoesNotPickARejectedStructurallyFlawedMove() {
        var forager = new AcceptedLocalSearchForager<TestdataSolution>(new HighestScoreFinalistPodium<>(),
                LocalSearchPickEarlyType.NEVER, Integer.MAX_VALUE, false);
        var phaseScope = createPhaseScope();
        forager.phaseStarted(phaseScope);
        var stepScope = new LocalSearchStepScope<>(phaseScope);
        forager.stepStarted(stepScope);

        var flawedMoveScope = new LocalSearchMoveScope<>(stepScope, 0, new SelectorBasedDummyMove());
        flawedMoveScope.setInitializedScore(new SimpleScoreDefinition().getStructurallyFlawedScore());
        // As AbstractAcceptor.isAccepted does for every structurally flawed move.
        flawedMoveScope.setAccepted(false);
        forager.addMove(flawedMoveScope);

        assertThat(forager.pickMove(stepScope))
                .withFailMessage("The forager picked a rejected, structurally flawed move as the step; "
                        + "executing it throws in MoveDirector.execute.")
                .isNull();
    }

    private static TestdataDependencyNoInconsistentFieldSolution buildProblem(int entityCount, int chainCount,
            int chainLength) {
        var entities = new ArrayList<TestdataDependencyNoInconsistentFieldEntity>();
        for (var i = 0; i < entityCount; i++) {
            entities.add(new TestdataDependencyNoInconsistentFieldEntity("m" + i));
        }
        var values = new ArrayList<TestdataDependencyNoInconsistentFieldValue>();
        for (var chain = 0; chain < chainCount; chain++) {
            TestdataDependencyNoInconsistentFieldValue previous = null;
            for (var index = 0; index < chainLength; index++) {
                var value = new TestdataDependencyNoInconsistentFieldValue("c" + chain + "j" + index,
                        Duration.ofMinutes(10L + index));
                if (previous != null) {
                    value.setDependencies(List.of(previous));
                }
                previous = value;
                values.add(value);
            }
        }
        return new TestdataDependencyNoInconsistentFieldSolution(entities, values);
    }

    private static LocalSearchPhaseScope<TestdataSolution> createPhaseScope() {
        var solverScope = new SolverScope<TestdataSolution>();
        var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
        InnerScoreDirector<TestdataSolution, SimpleScore> scoreDirector = mock(InnerScoreDirector.class);
        when(scoreDirector.getSolutionDescriptor()).thenReturn(TestdataSolution.buildSolutionDescriptor());
        when(scoreDirector.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
        solverScope.setScoreDirector(scoreDirector);
        solverScope.setWorkingRandom(new TestRandom(1, 1));
        solverScope.setInitializedBestScore(SimpleScore.of(-10));
        solverScope.setSolverMetricSet(EnumSet.of(SolverMetric.MOVE_EVALUATION_COUNT));
        var lastStepScope = new LocalSearchStepScope<>(phaseScope);
        lastStepScope.setInitializedScore(SimpleScore.of(-100));
        phaseScope.setLastCompletedStepScope(lastStepScope);
        return phaseScope;
    }

}
