package gq.luma.bot.render.fs.weighters;

public class GaussianDemoWeighter implements DemoWeighter {

    private static final double weightResolution = 1024d;
    private final double mean;
    private final double stdDev;
    private final double variance;

    private double[] averagedResults;

    public GaussianDemoWeighter(){
        this(0.5, 1d);
    }

    public GaussianDemoWeighter(double mean, double variance){
        this.mean = mean;
        this.variance = variance;
        this.stdDev = Math.sqrt(variance);
    }

    @Override
    public double weight(int framePosition) {
        final double x = framePosition * 2d - 1d;
        return Math.pow(Math.exp(-(((x - mean) * (x - mean)) / (2 * variance))), 1 / (stdDev * Math.sqrt(2 * Math.PI)));
    }

    @Override
    public boolean wantsAverage() {
        return true;
    }
}
