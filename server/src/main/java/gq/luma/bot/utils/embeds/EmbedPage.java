package gq.luma.bot.utils.embeds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmbedPage {
    private List<Object[]> fields = new ArrayList<>();

    public List<Object[]> getFields() {
        return fields;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder("EmbedPage: [");
        for(Object[] field : fields){
            sb.append(Arrays.deepToString(field));
            sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
