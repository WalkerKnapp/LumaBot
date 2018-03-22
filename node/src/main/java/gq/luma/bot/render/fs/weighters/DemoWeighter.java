package gq.luma.bot.render.fs.weighters;

public interface DemoWeighter {
    double weight(int framePosition);

    boolean wantsAverage();
}
