package gq.luma.bot.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.net.SocketException;

public class LoggingFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent event) {
        final IThrowableProxy throwableProxy = event.getThrowableProxy();
        if(throwableProxy instanceof ThrowableProxy){
            final ThrowableProxy throwableProxyImpl = (ThrowableProxy)throwableProxy;
            final Throwable throwable = throwableProxyImpl.getThrowable();
            if(throwable instanceof SocketException && throwable.getMessage().contains("Write failed")){
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
