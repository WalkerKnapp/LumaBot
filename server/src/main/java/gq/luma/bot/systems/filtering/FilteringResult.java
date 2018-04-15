package gq.luma.bot.systems.filtering;

public class FilteringResult {
    public boolean okay;
    public String message;

    public FilteringResult(boolean okay, String message){
        this.okay = okay;
        this.message = message;
    }

    public boolean isOkay() {
        return okay;
    }

    public String getMessage() {
        return message;
    }
}
