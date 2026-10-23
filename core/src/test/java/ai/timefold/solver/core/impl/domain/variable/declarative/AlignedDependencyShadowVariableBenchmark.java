package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.timefold.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.timefold.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.timefold.solver.core.impl.solver.DefaultSolver;
import ai.timefold.solver.core.testdomain.shadow.aligned_dependency.TestdataAlignedDependencyConstraintProvider;
import ai.timefold.solver.core.testdomain.shadow.aligned_dependency.TestdataAlignedDependencyEntity;
import ai.timefold.solver.core.testdomain.shadow.aligned_dependency.TestdataAlignedDependencySolution;
import ai.timefold.solver.core.testdomain.shadow.aligned_dependency.TestdataAlignedDependencyValue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Measures the move evaluation speed of local search on generated {@link TestdataAlignedDependencySolution} problems.
 * <p>
 * It is not part of the test suite, as its class name does not end with "Test".
 * Run it explicitly on a quiet machine; it takes a few minutes:
 *
 * <pre>
 * ./mvnw -pl core -am test -Dtest=AlignedDependencyShadowVariableBenchmark -Dsurefire.failIfNoSpecifiedTests=false
 * grep "moves/s" core/target/surefire-reports/*AlignedDependencyShadowVariableBenchmark-output.txt
 * </pre>
 * <p>
 * Each problem comes from a fixed seed and starts with every value assigned,
 * and the solver stops after a fixed number of moves in a reproducible environment mode.
 * So every run evaluates the same moves, and the best score shows it:
 * compare the speed of two solver versions only when their best scores are equal.
 * <p>
 * The scenarios: without aligned values, each value with a dependency can keep all its variables in one graph node.
 * With every chain aligned, each value needs two nodes, so that scenario shows the noise between two runs.
 */
class AlignedDependencyShadowVariableBenchmark {

    private static final Logger SOLVER_LOGGER = (Logger) LoggerFactory.getLogger("ai.timefold.solver");
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AlignedDependencyShadowVariableBenchmark.class);
    private static final OffsetDateTime START = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final int ENTITY_COUNT = 20;
    private static final int CHAIN_COUNT = 100;
    private static final int CHAIN_LENGTH = 10;
    private static final long MOVE_COUNT_LIMIT = 10_000L;
    private static final int WARM_UP_ROUND_COUNT = 1;
    private static final int MEASURED_ROUND_COUNT = 3;

    private static Level originalSolverLogLevel;

    @BeforeAll
    static void logLessDuringTheBenchmark() {
        // The test logging configuration logs every step, which would dominate the measurement.
        originalSolverLogLevel = SOLVER_LOGGER.getLevel();
        SOLVER_LOGGER.setLevel(Level.INFO);
    }

    @AfterAll
    static void restoreLogging() {
        SOLVER_LOGGER.setLevel(originalSolverLogLevel);
    }

    /**
     * @param alignedChainPercentage the share of chains that start each of their values
     *        at the same time as the value at the same position in a partner chain
     */
    @ParameterizedTest(name = "{0} % of the chains aligned")
    @ValueSource(ints = { 0, 50, 100 })
    void localSearchSpeed(int alignedChainPercentage) {
        for (var round = 0; round < WARM_UP_ROUND_COUNT; round++) {
            solve(alignedChainPercentage);
        }
        var speeds = new ArrayList<Long>();
        for (var round = 1; round <= MEASURED_ROUND_COUNT; round++) {
            var result = solve(alignedChainPercentage);
            speeds.add(result.moveEvaluationSpeed());
            LOGGER.info("{} % of the chains aligned, round {}: {} moves in {} ms, {} moves/s, best score {}",
                    alignedChainPercentage, round, result.moveCount(), result.timeMillis(), result.moveEvaluationSpeed(),
                    result.bestScore());
        }
        speeds.sort(null);
        LOGGER.info("{} % of the chains aligned: median {} moves/s", alignedChainPercentage,
                speeds.get(speeds.size() / 2));
    }

    private static Result solve(int alignedChainPercentage) {
        var solverConfig = new SolverConfig()
                .withSolutionClass(TestdataAlignedDependencySolution.class)
                .withEntityClasses(TestdataAlignedDependencyEntity.class, TestdataAlignedDependencyValue.class)
                .withConstraintProviderClass(TestdataAlignedDependencyConstraintProvider.class)
                .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(MOVE_COUNT_LIMIT));
        var solver = (DefaultSolver<TestdataAlignedDependencySolution>) SolverFactory
                .<TestdataAlignedDependencySolution> create(solverConfig)
                .buildSolver();
        var localSearchMeasurement = new LocalSearchMeasurement();
        solver.addPhaseLifecycleListener(localSearchMeasurement);
        var bestSolution = solver.solve(generateProblem(alignedChainPercentage));
        return new Result(localSearchMeasurement.moveCount, localSearchMeasurement.timeMillis,
                localSearchMeasurement.moveEvaluationSpeed, bestSolution.getScore());
    }

    /**
     * Each value depends on the value before it in its chain.
     * The aligned chains come in pairs, and a value in one starts with the value at the same position in the other.
     * Every value is assigned in index order, round-robin over the entities,
     * so a value comes after everything it depends on, and the problem starts without any loop.
     */
    static TestdataAlignedDependencySolution generateProblem(int alignedChainPercentage) {
        var random = new Random(37);
        var entities = IntStream.range(0, ENTITY_COUNT)
                .mapToObj(i -> new TestdataAlignedDependencyEntity("E" + i, START))
                .toList();
        var values = new ArrayList<TestdataAlignedDependencyValue>(CHAIN_COUNT * CHAIN_LENGTH);
        for (var chain = 0; chain < CHAIN_COUNT; chain++) {
            for (var position = 0; position < CHAIN_LENGTH; position++) {
                var duration = Duration.ofMinutes(10 + random.nextInt(50));
                var dependencies = position == 0 ? List.<TestdataAlignedDependencyValue> of() : List.of(values.getLast());
                var value = new TestdataAlignedDependencyValue("V" + values.size(), duration, dependencies);
                entities.get(values.size() % ENTITY_COUNT).getValues().add(value);
                values.add(value);
            }
        }
        var alignedChainCount = CHAIN_COUNT * alignedChainPercentage / 100 / 2 * 2;
        for (var chain = 0; chain < alignedChainCount; chain += 2) {
            for (var position = 0; position < CHAIN_LENGTH; position++) {
                TestdataAlignedDependencyValue.align(values.get(chain * CHAIN_LENGTH + position),
                        values.get((chain + 1) * CHAIN_LENGTH + position));
            }
        }
        return new TestdataAlignedDependencySolution(entities, values);
    }

    private record Result(long moveCount, long timeMillis, long moveEvaluationSpeed, HardSoftScore bestScore) {
    }

    private static final class LocalSearchMeasurement extends PhaseLifecycleListenerAdapter<TestdataAlignedDependencySolution> {

        private long moveCount;
        private long timeMillis;
        private long moveEvaluationSpeed;

        @Override
        public void phaseEnded(AbstractPhaseScope<TestdataAlignedDependencySolution> phaseScope) {
            if (phaseScope instanceof LocalSearchPhaseScope<TestdataAlignedDependencySolution>) {
                moveCount = phaseScope.getPhaseMoveEvaluationCount();
                timeMillis = phaseScope.calculatePhaseTimeMillisSpentUpToNow();
                moveEvaluationSpeed = moveCount * 1_000L / Math.max(1L, timeMillis);
            }
        }
    }
}
