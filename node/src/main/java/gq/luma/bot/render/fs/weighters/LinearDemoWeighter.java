package gq.luma.bot.render.fs.weighters;

public class LinearDemoWeighter implements DemoWeighter {

    private double frameWeight;

    public LinearDemoWeighter(int fpf){
        this.frameWeight = 1d/fpf;
    }

    @Override
    public double weight(int framePosition) {
        return frameWeight;
    }
}
