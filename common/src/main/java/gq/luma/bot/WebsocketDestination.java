package gq.luma.bot;

public enum  WebsocketDestination {
    WEBSERVER(0),
    GOOGLE_DRIVE(1),
    DISCORD(2);

    private int id;

    WebsocketDestination(int id){
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static WebsocketDestination getById(int id){
        for(WebsocketDestination d : values()){
            if(d.getId() == id){
                return d;
            }
        }
        return null;
    }
}
