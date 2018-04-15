package gq.luma.bot.systems.filtering.filters.types;

import gq.luma.bot.systems.filtering.FilteringResult;

import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class TextFilter extends Filter {
    protected TextFilter(ResultSet rs) throws SQLException {
        super(rs);
    }

    public abstract FilteringResult checkText(String text);
}
