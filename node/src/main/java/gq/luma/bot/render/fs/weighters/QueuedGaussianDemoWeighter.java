package gq.luma.bot.render.fs.weighters;

import java.util.Arrays;

public class QueuedGaussianDemoWeighter implements DemoWeighter {

    private double[] queue;

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

        System.out.println("Generated gaussian array with params fpf=" + fpf + ",mean=" + mean + ",variance=" + variance + ": " + Arrays.toString(queue));
    }

    @Override
    public double weight(int framePosition) {
        return queue[framePosition];
    }
}
