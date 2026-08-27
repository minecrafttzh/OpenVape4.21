package gg.vape.tools;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.nn.Activation;
import ai.djl.nn.Blocks;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.BatchNorm;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Sanity check: runs the same 6-feature input through both DJL and the
 * pure-Java {@code gg.vape.deeplearn.models.MlpModel}, and asserts the
 * outputs match to within 1e-4. MlpModel is loaded by reflection so this
 * tools sourceSet does not need to compile against main.
 *
 * <p>Run with: {@code ./gradlew runVerifyMlp --args="<model.bin> <model.params>"}</p>
 */
public final class VerifyMlp {

    private VerifyMlp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: VerifyMlp <model.bin> <model.params>");
            System.exit(1);
        }
        String binPath = args[0];
        String paramsPath = args[1];

        float[][] testInputs = {
                {0f, 0f, 0f, 0f, 0f, 3f},
                {15.5f, -3.2f, 1.1f, -0.4f, 0.22f, 2.5f},
                {-90f, 25f, -3f, 1.5f, 0.45f, 4.0f},
                {45f, 0f, 5f, 0f, 0.0f, 1.5f},
                {180f, -45f, -8f, 2f, 0.6f, 5.5f}
        };

        // DJL reference model
        Model djlModel = Model.newInstance("verify");
        djlModel.setBlock(createMlpBlock(2L));
        try (InputStream stream = Files.newInputStream(Paths.get(paramsPath))) {
            djlModel.load(stream);
        } catch (MalformedModelException | IOException error) {
            System.err.println("DJL load failed: " + error);
            error.printStackTrace();
            System.exit(2);
        }

        // Pure-Java MlpModel from main sourceSet - loaded via reflection.
        Class<?> mlpClass = Class.forName("gg.vape.deeplearn.models.MlpModel");
        Method parse = mlpClass.getDeclaredMethod("parse", InputStream.class);
        parse.setAccessible(true);
        Object mlp;
        try (InputStream binStream = Files.newInputStream(Paths.get(binPath))) {
            mlp = parse.invoke(null, binStream);
        }
        if (mlp == null) {
            System.err.println("Pure-Java model load failed.");
            System.exit(3);
        }
        Method predict = mlpClass.getDeclaredMethod("predict", float[].class);
        predict.setAccessible(true);

        boolean allOk = true;
        try (NDManager manager = NDManager.newBaseManager();
             Predictor<float[], float[]> predictor = djlModel.newPredictor(new IdentityTranslator())) {
            for (int i = 0; i < testInputs.length; i++) {
                float[] input = testInputs[i];
                float[] djlOut = predictor.predict(input);
                float[] javaOut = (float[]) predict.invoke(mlp, input);

                System.out.printf("Input %d: %s%n", i, java.util.Arrays.toString(input));
                System.out.printf("  DJL  : %s%n", java.util.Arrays.toString(djlOut));
                System.out.printf("  Java : %s%n", java.util.Arrays.toString(javaOut));

                if (djlOut.length != javaOut.length) {
                    System.err.println("  -> LENGTH MISMATCH");
                    allOk = false;
                    continue;
                }
                float maxDiff = 0f;
                for (int j = 0; j < djlOut.length; j++) {
                    maxDiff = Math.max(maxDiff, Math.abs(djlOut[j] - javaOut[j]));
                }
                if (maxDiff > 1e-4f) {
                    System.err.printf("  -> MISMATCH maxDiff=%.6f%n", maxDiff);
                    allOk = false;
                } else {
                    System.out.printf("  -> OK (maxDiff=%.6f)%n", maxDiff);
                }
            }
        }
        if (allOk) {
            System.out.println("ALL TESTS PASSED - pure-Java MlpModel matches DJL within 1e-4.");
        } else {
            System.err.println("FAIL - outputs diverged.");
            System.exit(4);
        }
    }

    private static final class IdentityTranslator implements Translator<float[], float[]> {
        @Override public NDList processInput(TranslatorContext ctx, float[] input) {
            return new NDList(ctx.getNDManager().create(input));
        }
        @Override public float[] processOutput(TranslatorContext ctx, NDList list) {
            return list.head().toFloatArray();
        }
    }

    private static SequentialBlock createMlpBlock(long outputs) {
        return new SequentialBlock()
                .add(Linear.builder().setUnits(128).build())
                .add(Blocks.batchFlattenBlock())
                .add(BatchNorm.builder().build())
                .add(Activation.reluBlock())

                .add(Linear.builder().setUnits(64).build())
                .add(Blocks.batchFlattenBlock())
                .add(BatchNorm.builder().build())
                .add(Activation.reluBlock())

                .add(Linear.builder().setUnits(32).build())
                .add(Blocks.batchFlattenBlock())
                .add(BatchNorm.builder().build())
                .add(Activation.reluBlock())

                .add(Linear.builder().setUnits(outputs).build());
    }
}
