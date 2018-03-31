package gq.luma.bot.render.fs.weighters;

public class LinearDemoWeighter implements DemoWeighter {

    private double frameWeight;
    private float floatWeight;

    public LinearDemoWeighter(int fpf){
        this.frameWeight = 1d/fpf;
        this.floatWeight = 1f/fpf;
    }

    @Override
    public double weight(int framePosition) {
        return frameWeight;
    }

    @Override
    public float weightFloat(int framePosition) {
        return floatWeight;
    }
}
