package gq.luma.bot.systems.filtering.filters;

import com.eclipsesource.json.JsonValue;
import gq.luma.bot.systems.filtering.FilteringResult;
import gq.luma.bot.systems.filtering.filters.types.TextFilter;
import gq.luma.bot.utils.StringUtilities;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LinkFilter extends TextFilter {
    private static UrlValidator urlValidator = new UrlValidator();

    private int type;
    private List<String> links;
    private List<Pattern> associatedPatterns;

    public LinkFilter(ResultSet rs) throws SQLException {
        super(rs);
        this.type = typeSettings.get("type").asInt();
        if(this.type == 0){
            this.links = typeSettings.get("links").asArray().values().stream().map(JsonValue::asString).collect(Collectors.toList());
            this.associatedPatterns = new ArrayList<>();
            this.links.forEach(s -> associatedPatterns.add(Pattern.compile(s.split("\\.")[0] +
                    "(dot|period|point|\\.)" +
                    s.split("\\.")[1])));
        }
    }

    @Override
    public FilteringResult checkText(String text) {
        if(type == 0){
            for(String part : StringUtilities.splitString(text)){
                try {
                    if (urlValidator.isValid(part) && links.contains(new URL(part).getHost())){
                        return new FilteringResult(false, "Message includes url from host: " + part);
                    }
                } catch (MalformedURLException ignored) {
                    //Will only happen if urlValidator#isValid fails.
                }
            }
            for(int i = 0; i < links.size(); i++){
                if(associatedPatterns.get(i).matcher(text.replaceAll("\\s|[^\\da-zA-Z\\-]", "")).matches()){
                    return new FilteringResult(false, "Message includes url from host: " + links.get(i));
                }
            }
        }
        return new FilteringResult(true, "Message does not contain url from any filtered host");
    }
}
