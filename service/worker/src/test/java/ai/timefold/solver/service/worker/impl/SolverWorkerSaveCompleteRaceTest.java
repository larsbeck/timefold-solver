package ai.timefold.solver.service.worker.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.inject.Instance;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.event.EventProducerId;
import ai.timefold.solver.service.definition.api.ModelConvertor;
import ai.timefold.solver.service.definition.api.SolverModel;
import ai.timefold.solver.service.definition.api.SolvingStatus;
import ai.timefold.solver.service.definition.api.domain.Metadata;
import ai.timefold.solver.service.worker.impl.testdata.TestdataModelInput;
import ai.timefold.solver.service.worker.impl.testdata.TestdataModelOutput;
import ai.timefold.solver.service.worker.impl.testdata.TestdataStorage;
import ai.timefold.solver.service.worker.impl.testdata.TestdataStorageService;
import ai.timefold.solver.service.worker.impl.testutil.RecordingEmitter;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reproduces a lost update between a throttled best-solution save and the run completion of {@link SolverWorker}.
 */
class SolverWorkerSaveCompleteRaceTest {

    private static final String RUN_ID = "run-1";

    @Test
    void completedStatusSurvivesABestSolutionSaveThatFinishesAfterTheCompletion() throws Exception {
        var storageService = new TestdataStorageService(new TestdataStorage(new ObjectMapper()));
        var firstConversionGate = new FirstConversionGate();
        var solverWorker = newSolverWorker(storageService, firstConversionGate);
        storeActiveRun(storageService);
        registerSolverJob(solverWorker);
        var solverModel = new FinishedSolverModel();

        var save = CompletableFuture
                .runAsync(() -> solverWorker.notifyOnSave(RUN_ID, solverModel, EventProducerId.localSearch(0)));
        assertThat(firstConversionGate.reached.await(10, TimeUnit.SECONDS)).isTrue();
        solverWorker.notifyOnComplete(RUN_ID, solverModel);
        assertThat(storageService.getMetadata(RUN_ID).getSolverStatus()).isEqualTo(SolvingStatus.SOLVING_COMPLETED);
        firstConversionGate.release.countDown();
        save.get(10, TimeUnit.SECONDS);

        assertThat(storageService.getMetadata(RUN_ID).getSolverStatus()).isEqualTo(SolvingStatus.SOLVING_COMPLETED);
    }

    private static void storeActiveRun(TestdataStorageService storageService) {
        storageService.storeModelInput(RUN_ID, new TestdataModelInput("input"));
        var metadata = new Metadata<HardSoftScore>("run", HardSoftScore.ZERO);
        metadata.setId(RUN_ID);
        metadata.setSolverStatus(SolvingStatus.SOLVING_ACTIVE);
        storageService.storeMetadata(RUN_ID, metadata);
    }

    @SuppressWarnings("unchecked")
    private static void registerSolverJob(SolverWorker solverWorker) throws ReflectiveOperationException {
        var solverJobsField = SolverWorker.class.getDeclaredField("solverJobs");
        solverJobsField.setAccessible(true);
        var solverJobs = (Map<Object, CompletableFuture<SolverJob<SolverModel>>>) solverJobsField.get(solverWorker);
        solverJobs.put(RUN_ID, CompletableFuture.completedFuture(stub(SolverJob.class)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static SolverWorker newSolverWorker(TestdataStorageService storageService,
            FirstConversionGate firstConversionGate) {
        var modelConvertor = (ModelConvertor) Proxy.newProxyInstance(ModelConvertor.class.getClassLoader(),
                new Class<?>[] { ModelConvertor.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "toModelOutput" -> firstConversionGate.convert();
                    case "applyOutputToInput" -> arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new SolverWorker(Optional.empty(), Optional.empty(), Optional.empty(), storageService, null, List.of(),
                null, null, modelConvertor, null, null, null, null, null, new ShutdownOnTerminate(null),
                stub(Instance.class), new CompletionStatus(), new RecordingEmitter<>(), new RecordingEmitter<>(),
                new RecordingEmitter<>(), new RecordingEmitter<>(), new RecordingEmitter<>(), new RecordingEmitter<>(),
                new RecordingEmitter<>(), new RecordingEmitter<>(), new RecordingEmitter<>(), new RecordingEmitter<>());
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, arguments) -> {
                    var returnType = method.getReturnType();
                    if (method.getName().equals("getProblemId")) {
                        return RUN_ID;
                    } else if (returnType == boolean.class) {
                        return false;
                    } else if (returnType == long.class) {
                        return 0L;
                    } else if (returnType == Duration.class) {
                        return Duration.ZERO;
                    }
                    return null;
                });
    }

    private static final class FinishedSolverModel implements SolverModel<HardSoftScore> {

        @Override
        public HardSoftScore getScore() {
            return HardSoftScore.of(0, -1);
        }

        @Override
        public ConstraintWeightOverrides<HardSoftScore> getConstraintWeightOverrides() {
            return ConstraintWeightOverrides.none();
        }
    }

    /**
     * Holds the first model output conversion, the one of the best-solution save, like a slow conversion of a large plan.
     */
    private static final class FirstConversionGate {

        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean firstConversion = new AtomicBoolean(true);

        TestdataModelOutput convert() throws InterruptedException {
            if (firstConversion.getAndSet(false)) {
                reached.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return new TestdataModelOutput();
        }
    }
}
