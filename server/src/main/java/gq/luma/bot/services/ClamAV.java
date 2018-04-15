package gq.luma.bot.services;

import fi.solita.clamav.ClamAVClient;
import gq.luma.bot.reference.FileReference;

import java.io.IOException;
import java.io.InputStream;

public class ClamAV implements Service {

    private ClamAVClient client;

    @Override
    public void startService() {
        client = new ClamAVClient(FileReference.clamAVLocation, 3310, 0);
    }

    public ClamAVResult scan(InputStream is) throws IOException {
        return new ClamAVResult(client.scan(is));
    }

    public class ClamAVResult{
        private static final String OK_RESULT = "OK";
        private static final String FOUND_RESULT_ORIGINAL = " FOUND";
        private static final String FOUND_RESULT_FORMATTED = " found";

        boolean okay;
        String message;

        private ClamAVResult(byte[] data){
            String result = new String(data);
            if(result.equals(OK_RESULT)){
                this.okay = true;
                this.message = result;
            } else {
                this.okay = false;
                this.message = result.replace(FOUND_RESULT_ORIGINAL, FOUND_RESULT_FORMATTED);
            }
        }

        public boolean isOkay() {
            return okay;
        }

        public String getMessage() {
            return message;
        }
    }
}
