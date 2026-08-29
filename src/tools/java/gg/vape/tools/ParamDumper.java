package gg.vape.tools;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Block;
import ai.djl.nn.Blocks;
import ai.djl.nn.Parameter;
import ai.djl.nn.ParameterList;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.BatchNorm;
import ai.djl.util.Pair;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standalone tool: loads the LiquidBounceNG {@code 21kc11kp.params} combat
 * regression model via DJL and dumps its weights into a simple self-describing
 * binary format ({@code model.bin}) that Vape can read with pure Java.
 *
 * <p>Run with: {@code ./gradlew runParamDumper --args="in.params out.bin"}</p>
 *
 * <p>{@code model.bin} layout (little-endian):</p>
 * <pre>
 *   magic   : 4 bytes  = "VMLP"
 *   version : int32     = 1
 *   nParams : int32
 *   for each param:
 *     nameLen  : int32
 *     name     : UTF-8 bytes (nameLen)
 *     ndim     : int32
 *     shape    : int32[ndim]
 *     dataLen  : int32   (= product(shape) for float32)
 *     data     : float32[dataLen] (little-endian)
 * </pre>
 */
public final class ParamDumper {

    private ParamDumper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ParamDumper <input.params> <output.bin>");
            System.exit(1);
        }
        File in = new File(args[0]);
        File out = new File(args[1]);
        if (!in.isFile()) {
            throw new IOException("Input params file not found: " + in);
        }

        System.out.println("Loading model from " + in + " (size=" + in.length() + ")");

        // Reconstruct the same MLP block used by LiquidBounceNG's ModelWrapper.
        Model model = Model.newInstance("21KC11KP");
        model.setBlock(createMlpBlock(2L));

        try (InputStream stream = Files.newInputStream(in.toPath())) {
            model.load(stream);
            System.out.println("Model loaded successfully.");
        } catch (MalformedModelException | IOException error) {
            System.err.println("Failed to load model: " + error);
            error.printStackTrace();
            System.exit(2);
        }

        // Extract every parameter array (weight/bias of Linear + BatchNorm params).
        try (NDManager manager = NDManager.newBaseManager()) {
            ParameterList params = model.getBlock().getParameters();
            System.out.println("Parameter count: " + params.size());
            for (Pair<String, Parameter> entry : params) {
                Parameter p = entry.getValue();
                NDArray arr = p.getArray();
                Shape shape = arr.getShape();
                System.out.println("  " + p.getName() + " shape=" + shape);
            }

            try (DataOutputStream dos = new DataOutputStream(
                    Files.newOutputStream(Paths.get(out.getAbsolutePath())))) {
                // Write magic + version
                dos.writeBytes("VMLP");
                dos.writeInt(1);
                dos.writeInt(params.size());
                for (Pair<String, Parameter> entry : params) {
                    Parameter p = entry.getValue();
                    NDArray arr = p.getArray();
                    String name = p.getName() == null ? "" : p.getName();
                    byte[] nameBytes = name.getBytes("UTF-8");
                    dos.writeInt(nameBytes.length);
                    dos.write(nameBytes);

                    Shape shape = arr.getShape();
                    dos.writeInt((int) shape.dimension());
                    for (long dim : shape.getShape()) {
                        dos.writeInt((int) dim);
                    }

                    // Float32 data, little-endian (matches ByteBuffer order below).
                    float[] data = arr.toFloatArray();
                    dos.writeInt(data.length);
                    ByteBuffer bb = ByteBuffer.allocate(data.length * 4)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    for (float v : data) {
                        bb.putFloat(v);
                    }
                    dos.write(bb.array());
                }
                dos.flush();
            }
            System.out.println("Wrote " + out + " (size=" + out.length() + ")");
        }
    }

    /**
     * Identical MLP layout to LiquidBounceNG ModelWrapper.createMlpBlock.
     */
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
