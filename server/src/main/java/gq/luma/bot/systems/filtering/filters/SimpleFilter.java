package gq.luma.bot.systems.filtering.filters;

import com.eclipsesource.json.JsonValue;
import gq.luma.bot.systems.filtering.FilteringResult;
import gq.luma.bot.systems.filtering.filters.types.TextFilter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleFilter extends TextFilter {
    private List<String> blacklist;
    private boolean regex;

    public SimpleFilter(ResultSet rs) throws SQLException {
        super(rs);
        this.blacklist = typeSettings.get("blacklist").asArray().values().stream().map(JsonValue::asString).collect(Collectors.toList());
        this.regex = typeSettings.get("regex").asBoolean();
    }

    @Override
    public FilteringResult checkText(String text) {
        if(regex) {
            for (String testString : blacklist) {
                if (text.matches(testString)) {
                    return new FilteringResult(false, "Message contains word prohibited by filter");
                }
            }
        } else {
            for (String testString : blacklist) {
                if (text.contains(testString)) {
                    return new FilteringResult(false, "Message contains word prohibited by filter");
                }
            }
        }
        return new FilteringResult(true, "Message cleared all text checks");
    }
}
