package gq.luma.bot.systems.filtering.filters.types;

import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.systems.filtering.FilteringResult;
import gq.luma.bot.LumaException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public abstract class FileFilter extends Filter {
    protected FileFilter(ResultSet rs) throws SQLException {
        super(rs);
    }

    public abstract Collection<InputType> checkTypes();

    public abstract FilteringResult checkInputStream(InputStream is) throws IOException, LumaException;
}
