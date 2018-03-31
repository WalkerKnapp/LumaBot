package gq.luma.bot.render.fs.weighters;

import java.util.Arrays;

public class QueuedGaussianDemoWeighter implements DemoWeighter {

    private double[] queue;
    private float[] floatQueue;

    public QueuedGaussianDemoWeighter(int fpf, double mean, double variance){
        this.queue = new double[fpf];

        double sum = 0;

        for(int i = 0; i < fpf; i++){
            final double x = i * 2d - 1d;
            queue[i] = Math.pow(Math.exp(-(((x - mean) * (x - mean)) / (2 * variance))), 1 / (Math.sqrt(variance) * Math.sqrt(2 * Math.PI)));
            sum += queue[i];
        }

        for(int i = 0; i < fpf; i++){
            queue[i] *= (1d/sum);
        }

        System.out.println("Generated double gaussian array with params fpf=" + fpf + ",mean=" + mean + ",variance=" + variance + ": " + Arrays.toString(queue));

        this.floatQueue = new float[fpf];

        float floatSum = 0;
        for(int i = 0; i < fpf; i++){
            final float x = i * 2f - 1f;
            floatQueue[i] = (float) Math.pow(Math.exp(-(((x - mean) * (x - mean)) / (2 * variance))), 1 / (Math.sqrt(variance) * Math.sqrt(2 * Math.PI)));
            floatSum += floatQueue[i];
        }

        for(int i = 0; i < fpf; i++){
            floatQueue[i] *= (1f/floatSum);
        }

        System.out.println("Generated float gaussian array with params fpf=" + fpf + ",mean=" + mean + ",variance=" + variance + ": " + Arrays.toString(floatQueue));
    }

    @Override
    public double weight(int framePosition) {
        return queue[framePosition];
    }

    @Override
    public float weightFloat(int framePosition) {
        return floatQueue[framePosition];
    }
}
