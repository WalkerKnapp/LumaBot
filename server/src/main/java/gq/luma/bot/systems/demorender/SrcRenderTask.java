package gq.luma.bot.systems.demorender;

import gq.luma.bot.services.node.Task;

public abstract class SrcRenderTask implements Task {

    private boolean rendering;

    @Override
    public boolean isRendering(){
        return rendering;
    }

    @Override
    public void setRendering(boolean b){
        this.rendering = b;
    }

}
