package com.forge.audio.engine;

import com.jsyn.ports.UnitInputPort;
import com.jsyn.unitgen.UnitGenerator;

/**
 * JSyn {@link UnitGenerator} that accumulates audio samples from the master mix and
 * triggers {@link FftProcessor#process} every 256 samples.
 *
 * <p>This unit is added to the JSyn synthesizer graph and connected to the
 * master mixer output so that it receives the final mixed signal. It runs entirely
 * on the JSyn audio thread and writes analysis results to the shared {@link AnalysisBus}.
 *
 * <p>Typical usage:
 * <pre>
 *   AnalysisDriver driver = new AnalysisDriver(analysisBus);
 *   synth.add(driver);
 *   masterMixer.output.connect(0, driver.input, 0);
 *   driver.start();
 * </pre>
 */
public final class AnalysisDriver extends UnitGenerator {

    /** Mono input port — connect the master mix here. */
    public final UnitInputPort input;

    private final FftProcessor fftProcessor;
    private final AnalysisBus  analysisBus;

    // Accumulation buffer: gather exactly FftProcessor.SIZE samples before processing
    private final float[] accumBuffer = new float[FftProcessor.SIZE];
    private int           accumCount  = 0;

    public AnalysisDriver(AnalysisBus analysisBus) {
        this.analysisBus  = analysisBus;
        this.fftProcessor = new FftProcessor();

        input = new UnitInputPort("input");
        addPort(input);
    }

    @Override
    public void generate(int start, int limit) {
        double[] inputValues = input.getValues();

        for (int i = start; i < limit; i++) {
            // Cast double JSyn sample to float
            accumBuffer[accumCount++] = (float) inputValues[i];

            if (accumCount >= FftProcessor.SIZE) {
                fftProcessor.process(accumBuffer, analysisBus);
                accumCount = 0;
            }
        }
    }
}
