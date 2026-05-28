package com.octotask.bot.ai;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.translate.Batchifier;
import ai.djl.inference.Predictor;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Primary
@ConditionalOnProperty(prefix = "embeddings.djl", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DJLEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DJLEmbeddingService.class);
    private static final String RES_PREFIX = "/transformer/";
    private static final String ONNX_NAME = "sentence-transformers-paraphrase-multilingual-MiniLM-L12-v2.onnx";

    private HuggingFaceTokenizer tokenizer;
    private ZooModel<String, float[]> model;
    private int dimension = 384;

    @PostConstruct
    public void init() throws Exception {
        // copy resources to temp directory for DJL to load
        Path tmp = Files.createTempDirectory("djl-model-");
        tmp.toFile().deleteOnExit();

        // copy onnx
        try (InputStream is = getClass().getResourceAsStream(RES_PREFIX + ONNX_NAME)) {
            if (is == null)
                throw new IllegalStateException("ONNX model not found in resources: " + ONNX_NAME);
            Path modelFile = tmp.resolve("model.onnx");
            Files.copy(is, modelFile);
        }

        // copy tokenizer.json if present
        try (InputStream is = getClass().getResourceAsStream(RES_PREFIX + "tokenizer.json")) {
            if (is != null) {
                Path tokFile = tmp.resolve("tokenizer.json");
                Files.copy(is, tokFile);
                tokenizer = HuggingFaceTokenizer.builder().optTokenizerPath(tokFile).build();
            }
        }

        if (tokenizer == null) {
            // fallback: try sentencepiece model
            try (InputStream is = getClass().getResourceAsStream(RES_PREFIX + "sentencepiece.bpe.model")) {
                if (is != null) {
                    Path sp = tmp.resolve("sentencepiece.model");
                    Files.copy(is, sp);
                    tokenizer = HuggingFaceTokenizer.builder().optTokenizerPath(sp).build();
                }
            }
        }

        if (tokenizer == null) {
            log.warn("No tokenizer found in resources; DJLEmbeddingService will not be functional.");
        }

        // Build a simple translator that tokenizes and does mean pooling over tokens
        Translator<String, float[]> translator = new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, String input) throws Exception {
                if (tokenizer == null)
                    throw new IllegalStateException("Tokenizer not initialized");
                // tokenize -> use long[] and INT64 tensors to match fixed ONNX inputs
                long[] ids = tokenizer.encode(input).getIds();
                NDManager nm = ctx.getNDManager();
                // produce 1D tensors (seq_len,) so DJL batching produces (batch_size, seq_len)
                NDArray inputIds = nm.create(ids); // shape [seq_len], dtype INT64
                NDArray attention = nm.ones(new Shape(ids.length), DataType.INT64);
                NDArray tokenTypeIds = nm.zeros(new Shape(ids.length), DataType.INT64);
                return new NDList(inputIds, attention, tokenTypeIds);
            }

            @Override
            public float[] processOutput(TranslatorContext ctx, NDList list) throws Exception {
                // handle token embeddings output which may be [batch, seq_len, hidden]
                // or [seq_len, hidden] depending on export/runtime. We produce a single
                // sentence embedding of size `hidden` via mean pooling.
                NDArray out = list.get(0);
                try (NDManager sub = out.getManager().newSubManager()) {
                    NDArray mask = null;
                    if (list.size() > 1)
                        mask = list.get(1);

                    float[] array;
                    long[] shape = out.getShape().getShape();
                    if (shape.length == 3) {
                        // [batch, seq, hidden]
                        NDArray summed = out.sum(new int[] { 1 }); // -> [batch, hidden]
                        if (mask != null) {
                            NDArray lengths = mask.sum(new int[] { 1 }).toType(out.getDataType(), false);
                            summed = summed.div(lengths.expandDims(1));
                        } else {
                            long seq = out.getShape().get(1);
                            summed = summed.div((float) seq);
                        }
                        // assume batch==1 for predict -> take first row
                        if (summed.getShape().dimension() == 2)
                            array = summed.get(0).toFloatArray();
                        else
                            array = summed.toFloatArray();
                    } else if (shape.length == 2) {
                        // [seq, hidden]
                        // sum over seq axis (0) -> [hidden]
                        NDArray summed = out.sum(new int[] { 0 });
                        if (mask != null) {
                            NDArray lengths = mask.sum(new int[] { 0 }).toType(out.getDataType(), false);
                            summed = summed.div((float) lengths.getFloat(0));
                        } else {
                            long seq = out.getShape().get(0);
                            summed = summed.div((float) seq);
                        }
                        array = summed.toFloatArray();
                    } else {
                        throw new IllegalStateException(
                                "Unexpected output shape from model: " + java.util.Arrays.toString(shape));
                    }

                    return array;
                }
            }

            @Override
            public Batchifier getBatchifier() {
                return Batchifier.STACK;
            }
        };

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelPath(tmp)
                .optEngine("OnnxRuntime")
                .optTranslator(translator)
                .build();

        try {
            model = criteria.loadModel();
            // attempt to probe dimension by running a dummy input
            try (Predictor<String, float[]> p = model.newPredictor()) {
                float[] v = p.predict("test");
                this.dimension = v.length;
                log.info("Loaded ONNX model, embedding dimension={}", this.dimension);
            }
        } catch (ModelNotFoundException | TranslateException e) {
            log.error("Failed to load DJL ONNX model", e);
            throw e;
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) throws Exception {
        if (model == null)
            throw new IllegalStateException("Model not loaded");
        List<float[]> out = new ArrayList<>(texts.size());
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            for (String t : texts) {
                out.add(predictor.predict(t));
            }
        }
        return out;
    }

    @Override
    public int getDimension() {
        return dimension;
    }
}
