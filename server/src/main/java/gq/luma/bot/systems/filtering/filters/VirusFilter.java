package gq.luma.bot.systems.filtering.filters;

import gq.luma.bot.Luma;
import gq.luma.bot.commands.params.io.input.InputType;
import gq.luma.bot.services.ClamAV;
import gq.luma.bot.systems.filtering.filters.types.FileFilter;
import gq.luma.bot.systems.filtering.FilteringResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class VirusFilter extends FileFilter {
    private static final Logger logger = LoggerFactory.getLogger(VirusFilter.class);
    private String scanner;

    public VirusFilter(ResultSet rs) throws SQLException {
        super(rs);
        this.scanner = typeSettings.get("scanner").asString();
    }

    @Override
    public Collection<InputType> checkTypes() {
        return List.of(InputType.DOCUMENT, InputType.EXECUTABLE, InputType.COMPRESSED);
    }

    @Override
    public FilteringResult checkInputStream(InputStream is) throws IOException {
        logger.debug("Scanning potential virus...");
        //ClamAV.ClamAVResult result = Luma.clamAV.scan(is);
        //logger.debug("Got result: " + result.isOkay() + " " + result.getMessage());
        //return new FilteringResult(result.isOkay(), result.getMessage());
        return null;
    }
}
